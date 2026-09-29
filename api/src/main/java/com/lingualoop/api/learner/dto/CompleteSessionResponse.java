package com.lingualoop.api.learner.dto;

import java.time.Instant;

public record CompleteSessionResponse(Long id, Instant startedAt, Instant endedAt, String variantKey,
        long attemptCount, Double averageGrade) {
}
