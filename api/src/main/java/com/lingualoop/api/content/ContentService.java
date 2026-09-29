package com.lingualoop.api.content;

import java.util.List;

import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.common.error.NotFoundException;
import com.lingualoop.api.content.dto.ExerciseDto;
import com.lingualoop.api.content.dto.LanguageDto;
import com.lingualoop.api.content.dto.LessonDto;
import com.lingualoop.api.content.dto.UnitDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {

    private final LanguageRepository languages;
    private final UnitRepository units;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;

    public ContentService(LanguageRepository languages, UnitRepository units, LessonRepository lessons,
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

    @Transactional(readOnly = true)
    public List<UnitDto> getUnitsByLanguage(Long languageId) {
        if (!languages.existsById(languageId)) {
            throw new NotFoundException("Language " + languageId + " not found");
        }
        return units.findByLanguageIdOrderByPositionAsc(languageId).stream()
                .map(unit -> new UnitDto(unit.getId(), languageId, unit.getTitle(), unit.getPosition(),
                        lessons.findByUnitIdOrderByPositionAsc(unit.getId()).stream()
                                .map(lesson -> new UnitDto.LessonSummary(lesson.getId(), lesson.getTitle(),
                                        lesson.getPosition(), exercises.countByLessonId(lesson.getId())))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitDto getUnit(Long unitId) {
        Unit unit = units.findById(unitId)
                .orElseThrow(() -> new NotFoundException("Unit " + unitId + " not found"));
        List<UnitDto.LessonSummary> lessonSummaries = lessons.findByUnitIdOrderByPositionAsc(unitId).stream()
                .map(lesson -> new UnitDto.LessonSummary(lesson.getId(), lesson.getTitle(), lesson.getPosition(),
                        exercises.countByLessonId(lesson.getId())))
                .toList();
        return new UnitDto(unit.getId(), unit.getLanguage().getId(), unit.getTitle(), unit.getPosition(),
                lessonSummaries);
    }

    @Transactional(readOnly = true)
    public LessonDto getLesson(Long lessonId) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson " + lessonId + " not found"));
        List<ExerciseDto> exerciseDtos = exercises.findByLessonIdOrderByIdAsc(lessonId).stream()
                .map(exercise -> new ExerciseDto(
                        exercise.getId(),
                        exercise.getType(),
                        exercise.getPrompt(),
                        exercise.getAnswer(),
                        exercise.getChoices(),
                        exercise.getCaption(),
                        exercise.getAudioAsset() == null ? null : AudioAssetDto.from(exercise.getAudioAsset())))
                .toList();
        return new LessonDto(lesson.getId(), lesson.getUnit().getId(), lesson.getTitle(), lesson.getPosition(),
                exerciseDtos);
    }
}
