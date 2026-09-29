package com.lingualoop.api.learner.dto;

import java.time.LocalDate;

public record StatsResponse(long attemptsTotal, long exercisesStudied, long exercisesMastered, Double averageGrade,
        long dueNow, long sessionsCompleted, StreakDto streak) {

    public record StreakDto(int currentDays, int longestDays, LocalDate lastActiveDate) {
    }
}
