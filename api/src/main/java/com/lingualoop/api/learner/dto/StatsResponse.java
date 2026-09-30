package com.lingualoop.api.learner.dto;

import java.time.LocalDate;
import java.util.List;

public record StatsResponse(long attemptsTotal, long exercisesStudied, long exercisesMastered, Double averageGrade,
        long dueNow, long sessionsCompleted, StreakDto streak, UnitMasteryDto unit) {

    public record StreakDto(int currentDays, int longestDays, LocalDate lastActiveDate) {
    }

    public record UnitMasteryDto(Long unitId, String title, double mastery, List<LessonMasteryDto> lessons) {
    }

    public record LessonMasteryDto(Long lessonId, String title, double mastery, long masteredExercises,
            long totalExercises) {
    }
}
