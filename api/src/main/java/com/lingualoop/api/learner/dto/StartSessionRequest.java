package com.lingualoop.api.learner.dto;

import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(@NotNull Long lessonId) {
}
