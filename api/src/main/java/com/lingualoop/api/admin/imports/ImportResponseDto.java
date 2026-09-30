package com.lingualoop.api.admin.imports;

import java.util.List;

/**
 * What an import did (or, in dry-run, would do). Re-importing an existing
 * (language, unit position) REPLACES the unit's children, so re-runs are
 * idempotent and dry-runs show exactly what would change.
 */
public record ImportResponseDto(
        boolean dryRun,
        String languageCode,
        String languageAction,
        String unitTitle,
        String unitAction,
        long previousExerciseCount,
        List<LessonResultDto> lessons,
        int lessonCount,
        int exerciseCount) {

    public record LessonResultDto(String title, int position, int exerciseCount) {
    }

    public static ImportResponseDto of(ImportService.Plan plan, UnitBundle bundle, boolean dryRun) {
        return new ImportResponseDto(
                dryRun,
                plan.languageCode(),
                plan.languageCreated() ? "CREATED" : "REUSED",
                bundle.unit.title(),
                plan.unitAction(),
                plan.previousExerciseCount(),
                bundle.lessons.stream()
                        .map(lesson -> new LessonResultDto(lesson.title(), lesson.position(),
                                lesson.exercises().size()))
                        .toList(),
                bundle.lessons.size(),
                bundle.totalExercises());
    }
}
