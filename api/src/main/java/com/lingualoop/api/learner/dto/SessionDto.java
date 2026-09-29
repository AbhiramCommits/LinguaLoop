package com.lingualoop.api.learner.dto;

import java.time.Instant;

public record SessionDto(Long id, Long lessonId, String variantKey, Instant startedAt, long exerciseCount) {
}
