package com.lingualoop.api.admin;

import java.util.List;

import com.lingualoop.api.admin.dto.ExerciseRequest;
import com.lingualoop.api.admin.dto.LanguageRequest;
import com.lingualoop.api.admin.dto.LessonRequest;
import com.lingualoop.api.admin.dto.UnitRequest;
import com.lingualoop.api.common.error.BadRequestException;
import com.lingualoop.api.common.error.ConflictException;
import com.lingualoop.api.common.error.NotFoundException;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.ExerciseType;
import com.lingualoop.api.content.Language;
import com.lingualoop.api.content.LanguageRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.content.UnitRepository;
import com.lingualoop.api.content.dto.ExerciseDto;
import com.lingualoop.api.content.dto.LanguageDto;
import com.lingualoop.api.content.dto.LessonDto;
import com.lingualoop.api.content.dto.UnitDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminContentService {

    private final LanguageRepository languages;
    private final UnitRepository units;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;

    public AdminContentService(LanguageRepository languages, UnitRepository units, LessonRepository lessons,
            ExerciseRepository exercises) {
        this.languages = languages;
        this.units = units;
        this.lessons = lessons;
        this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public List<LanguageDto> listLanguages() {
        return languages.findAllByOrderByIdAsc().stream()
                .map(language -> new LanguageDto(language.getId(), language.getCode(), language.getName(),
                        units.countByLanguageId(language.getId())))
                .toList();
    }

    @Transactional
    public LanguageDto createLanguage(LanguageRequest request) {
        if (languages.findByCode(request.code()).isPresent()) {
            throw new ConflictException("Language code '" + request.code() + "' already exists");
        }
        Language saved = languages.save(new Language(request.code().trim(), request.name().trim()));
        return toDto(saved);
    }

    @Transactional
    public LanguageDto updateLanguage(Long id, LanguageRequest request) {
        Language language = languages.findById(id)
                .orElseThrow(() -> new NotFoundException("Language " + id + " not found"));
        languages.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Language code '" + request.code() + "' already exists");
                });
        language.setName(request.name().trim());
        return toDto(languages.save(language));
    }

    @Transactional
    public void deleteLanguage(Long id) {
        if (!languages.existsById(id)) {
            throw new NotFoundException("Language " + id + " not found");
        }
        languages.deleteById(id);
    }

    @Transactional
    public UnitDto createUnit(UnitRequest request) {
        Language language = languages.findById(request.languageId())
                .orElseThrow(() -> new NotFoundException("Language " + request.languageId() + " not found"));
        if (units.existsByLanguageIdAndPosition(request.languageId(), request.position())) {
            throw new ConflictException("Position " + request.position() + " is already taken in this language");
        }
        Unit saved = units.save(new Unit(language, request.title().trim(), request.position()));
        return unitDto(saved, List.of());
    }

    @Transactional
    public UnitDto updateUnit(Long id, UnitRequest request) {
        Unit unit = units.findById(id).orElseThrow(() -> new NotFoundException("Unit " + id + " not found"));
        units.findAll().stream()
                .filter(other -> !other.getId().equals(id)
                        && other.getLanguage().getId().equals(request.languageId())
                        && other.getPosition() == request.position())
                .findAny()
                .ifPresent(other -> {
                    throw new ConflictException("Position " + request.position() + " is already taken in this language");
                });
        unit.setTitle(request.title().trim());
        unit.setPosition(request.position());
        return unitDto(units.save(unit), lessonSummaries(id));
    }

    @Transactional
    public void deleteUnit(Long id) {
        if (!units.existsById(id)) {
            throw new NotFoundException("Unit " + id + " not found");
        }
        units.deleteById(id);
    }

    @Transactional
    public LessonDto createLesson(LessonRequest request) {
        Unit unit = units.findById(request.unitId())
                .orElseThrow(() -> new NotFoundException("Unit " + request.unitId() + " not found"));
        if (lessons.existsByUnitIdAndPosition(request.unitId(), request.position())) {
            throw new ConflictException("Position " + request.position() + " is already taken in this unit");
        }
        Lesson saved = lessons.save(new Lesson(unit, request.title().trim(), request.position()));
        return lessonDto(saved, List.of());
    }

    @Transactional
    public LessonDto updateLesson(Long id, LessonRequest request) {
        Lesson lesson = lessons.findById(id).orElseThrow(() -> new NotFoundException("Lesson " + id + " not found"));
        lessons.findAll().stream()
                .filter(other -> !other.getId().equals(id)
                        && other.getUnit().getId().equals(request.unitId())
                        && other.getPosition() == request.position())
                .findAny()
                .ifPresent(other -> {
                    throw new ConflictException("Position " + request.position() + " is already taken in this unit");
                });
        lesson.setTitle(request.title().trim());
        lesson.setPosition(request.position());
        return lessonDto(lessons.save(lesson), exerciseDtos(id));
    }

    @Transactional
    public void deleteLesson(Long id) {
        if (!lessons.existsById(id)) {
            throw new NotFoundException("Lesson " + id + " not found");
        }
        lessons.deleteById(id);
    }

    @Transactional
    public ExerciseDto createExercise(ExerciseRequest request) {
        Lesson lesson = lessons.findById(request.lessonId())
                .orElseThrow(() -> new NotFoundException("Lesson " + request.lessonId() + " not found"));
        ExerciseType type = parseType(request.type());
        List<String> choices = request.choices() == null ? List.of() : request.choices();
        validateExercise(type, request.answer(), choices);
        Exercise saved = exercises.save(
                new Exercise(lesson, type, request.prompt().trim(), request.answer().trim()));
        saved.setChoices(choices);
        saved.setCaption(trimToNull(request.caption()));
        return toDto(exercises.save(saved));
    }

    @Transactional
    public ExerciseDto updateExercise(Long id, ExerciseRequest request) {
        Exercise exercise = exercises.findById(id)
                .orElseThrow(() -> new NotFoundException("Exercise " + id + " not found"));
        ExerciseType type = parseType(request.type());
        List<String> choices = request.choices() == null ? List.of() : request.choices();
        validateExercise(type, request.answer(), choices);
        exercise.setPrompt(request.prompt().trim());
        exercise.setAnswer(request.answer().trim());
        exercise.setChoices(choices);
        exercise.setCaption(trimToNull(request.caption()));
        return toDto(exercises.save(exercise));
    }

    @Transactional
    public void deleteExercise(Long id) {
        if (!exercises.existsById(id)) {
            throw new NotFoundException("Exercise " + id + " not found");
        }
        exercises.deleteById(id);
    }

    public static ExerciseType parseType(String type) {
        try {
            return ExerciseType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown exercise type '" + type + "' (use TRANSLATE, MULTIPLE_CHOICE or LISTEN)");
        }
    }

    static void validateExercise(ExerciseType type, String answer, List<String> choices) {
        switch (type) {
            case MULTIPLE_CHOICE -> {
                if (choices.size() < 2 || choices.size() > 8) {
                    throw new BadRequestException("MULTIPLE_CHOICE exercises need between 2 and 8 choices");
                }
                if (!choices.contains(answer.trim())) {
                    throw new BadRequestException("The answer must be one of the choices");
                }
            }
            case TRANSLATE, LISTEN -> {
                if (!choices.isEmpty()) {
                    throw new BadRequestException(type + " exercises must not define choices");
                }
            }
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LanguageDto toDto(Language language) {
        return new LanguageDto(language.getId(), language.getCode(), language.getName(),
                units.countByLanguageId(language.getId()));
    }

    private UnitDto unitDto(Unit unit, List<UnitDto.LessonSummary> lessonSummaries) {
        return new UnitDto(unit.getId(), unit.getLanguage().getId(), unit.getTitle(), unit.getPosition(),
                lessonSummaries);
    }

    private List<UnitDto.LessonSummary> lessonSummaries(Long unitId) {
        return lessons.findByUnitIdOrderByPositionAsc(unitId).stream()
                .map(lesson -> new UnitDto.LessonSummary(lesson.getId(), lesson.getTitle(), lesson.getPosition(),
                        exercises.countByLessonId(lesson.getId())))
                .toList();
    }

    private LessonDto lessonDto(Lesson lesson, List<ExerciseDto> exerciseDtos) {
        return new LessonDto(lesson.getId(), lesson.getUnit().getId(), lesson.getTitle(), lesson.getPosition(),
                exerciseDtos);
    }

    private List<ExerciseDto> exerciseDtos(Long lessonId) {
        return exercises.findByLessonIdOrderByIdAsc(lessonId).stream()
                .map(AdminContentService::toDto)
                .toList();
    }

    private static ExerciseDto toDto(Exercise exercise) {
        return new ExerciseDto(
                exercise.getId(),
                exercise.getType(),
                exercise.getPrompt(),
                exercise.getAnswer(),
                exercise.getChoices(),
                exercise.getCaption(),
                exercise.getAudioAsset() == null ? null
                        : com.lingualoop.api.audio.AudioAssetDto.from(exercise.getAudioAsset()));
    }
}
