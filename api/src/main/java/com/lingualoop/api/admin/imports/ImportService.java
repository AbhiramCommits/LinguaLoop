package com.lingualoop.api.admin.imports;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lingualoop.api.admin.AdminContentService;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.ExerciseType;
import com.lingualoop.api.content.Language;
import com.lingualoop.api.content.LanguageRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.content.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a whole unit bundle atomically: the bundle is fully parsed and
 * validated first (any failure aborts everything with per-row errors), and
 * persistence happens in a single transaction. Re-importing the same
 * (language code, unit position) replaces the unit's children, which makes
 * re-runs idempotent and gives dry-runs a meaningful "what would change".
 */
@Service
public class ImportService {

    private final LanguageRepository languages;
    private final UnitRepository units;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;

    public ImportService(LanguageRepository languages, UnitRepository units, LessonRepository lessons,
            ExerciseRepository exercises) {
        this.languages = languages;
        this.units = units;
        this.lessons = lessons;
        this.exercises = exercises;
    }

    public record Plan(UnitBundle bundle, boolean languageCreated, String unitAction,
            long previousExerciseCount, String languageCode) {
    }

    public Plan plan(UnitBundle bundle) {
        validate(bundle);
        Language language = languages.findByCode(bundle.language.code()).orElse(null);
        boolean languageCreated = language == null;
        String unitAction = "CREATED";
        long previousCount = 0;
        if (language != null) {
            Unit existing = units.findByLanguageIdOrderByPositionAsc(language.getId()).stream()
                    .filter(unit -> unit.getPosition() == bundle.unit.position())
                    .findFirst().orElse(null);
            if (existing != null) {
                unitAction = "REPLACED";
                previousCount = existingLessons(existing).stream()
                        .mapToLong(lesson -> exercises.countByLessonId(lesson.getId())).sum();
            }
        }
        return new Plan(bundle, languageCreated, unitAction, previousCount, bundle.language.code());
    }

    @Transactional
    public Plan apply(UnitBundle bundle) {
        validate(bundle);
        Plan plan = plan(bundle);

        Language language = languages.findByCode(bundle.language.code())
                .orElseGet(() -> languages.save(new Language(bundle.language.code(), bundle.language.name())));

        Unit unit = units.findByLanguageIdOrderByPositionAsc(language.getId()).stream()
                .filter(existing -> existing.getPosition() == bundle.unit.position())
                .findFirst().orElse(null);
        if (unit == null) {
            unit = units.save(new Unit(language, bundle.unit.title(), bundle.unit.position()));
        } else {
            unit.setTitle(bundle.unit.title());
            units.save(unit);
            for (Lesson oldLesson : existingLessons(unit)) {
                lessons.delete(oldLesson);
            }
            lessons.flush();
        }

        for (UnitBundle.LessonInfo lessonInfo : bundle.lessons) {
            Lesson lesson = lessons.save(new Lesson(unit, lessonInfo.title(), lessonInfo.position()));
            for (UnitBundle.ExerciseInfo exerciseInfo : lessonInfo.exercises()) {
                ExerciseType type = AdminContentService.parseType(exerciseInfo.type());
                Exercise exercise = exercises.save(new Exercise(lesson, type,
                        exerciseInfo.prompt(), exerciseInfo.answer()));
                exercise.setChoices(exerciseInfo.choices() == null ? List.of() : exerciseInfo.choices());
                exercise.setCaption(blankToNull(exerciseInfo.caption()));
                exercises.save(exercise);
            }
        }
        return plan;
    }

    /** Strict validation of the whole bundle; throws ImportValidationException with per-row errors. */
    public void validate(UnitBundle bundle) {
        List<RowError> errors = new ArrayList<>();

        if (!bundle.language.code().matches("[a-z]{2,3}(-[A-Z]{2})?")) {
            errors.add(new RowError(bundle.language.line(),
                    "Language code '" + bundle.language.code() + "' must look like 'es' or 'pt-BR'"));
        }
        if (bundle.language.name() == null || bundle.language.name().isBlank()) {
            errors.add(new RowError(bundle.language.line(), "Language name must not be blank"));
        }
        if (bundle.unit.title() == null || bundle.unit.title().isBlank()) {
            errors.add(new RowError(bundle.unit.line(), "Unit title must not be blank"));
        }
        if (bundle.unit.position() < 1) {
            errors.add(new RowError(bundle.unit.line(), "Unit position must be >= 1"));
        }
        if (bundle.lessons.isEmpty()) {
            errors.add(new RowError(bundle.unit.line(), "The unit must contain at least one lesson"));
        }

        Set<Integer> lessonPositions = new HashSet<>();
        int exerciseCount = 0;
        for (UnitBundle.LessonInfo lesson : bundle.lessons) {
            if (lesson.title() == null || lesson.title().isBlank()) {
                errors.add(new RowError(lesson.line(), "Lesson title must not be blank"));
            }
            if (lesson.position() < 1 || !lessonPositions.add(lesson.position())) {
                errors.add(new RowError(lesson.line(), "Lesson position " + lesson.position()
                        + " is missing or duplicated within the unit"));
            }
            if (lesson.exercises().isEmpty()) {
                errors.add(new RowError(lesson.line(), "Lesson '" + lesson.title() + "' has no exercises"));
            }
            for (UnitBundle.ExerciseInfo exercise : lesson.exercises()) {
                exerciseCount++;
                validateExercise(errors, exercise);
            }
        }
        if (exerciseCount == 0) {
            errors.add(new RowError(bundle.unit.line(), "The bundle contains no exercises"));
        }

        if (!errors.isEmpty()) {
            throw new ImportValidationException(errors);
        }
    }

    private void validateExercise(List<RowError> errors, UnitBundle.ExerciseInfo exercise) {
        int line = exercise.line();
        ExerciseType type;
        try {
            type = AdminContentService.parseType(exercise.type());
        } catch (RuntimeException ex) {
            errors.add(new RowError(line, ex.getMessage()));
            return;
        }
        if (isBlank(exercise.prompt())) {
            errors.add(new RowError(line, type + ": prompt must not be blank"));
        }
        if (isBlank(exercise.answer())) {
            errors.add(new RowError(line, type + ": answer must not be blank"));
        }
        List<String> choices = exercise.choices() == null ? List.of() : exercise.choices();
        switch (type) {
            case MULTIPLE_CHOICE -> {
                if (choices.size() < 2 || choices.size() > 8) {
                    errors.add(new RowError(line, "MULTIPLE_CHOICE needs between 2 and 8 choices"));
                }
                if (!choices.contains(exercise.answer())) {
                    errors.add(new RowError(line, "The answer must be one of the choices"));
                }
                if (choices.size() != new HashSet<>(choices).size()) {
                    errors.add(new RowError(line, "Choices must not contain duplicates"));
                }
            }
            case TRANSLATE, LISTEN -> {
                if (!choices.isEmpty()) {
                    errors.add(new RowError(line, type + " exercises must not define choices"));
                }
            }
        }
    }

    private List<Lesson> existingLessons(Unit unit) {
        return lessons.findByUnitIdOrderByPositionAsc(unit.getId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
