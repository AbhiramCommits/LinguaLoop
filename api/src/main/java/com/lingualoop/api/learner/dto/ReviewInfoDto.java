package com.lingualoop.api.learner.dto;

import java.time.Instant;

public record ReviewInfoDto(double easeFactor, double intervalDays, int repetitions, Instant dueAt, Short lastGrade,
        int lapses) {
}
