package com.lingualoop.api.learner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AttemptRequest(
        @NotNull Long exerciseId,
        @NotNull @Min(0) @Max(5) Integer grade,
        @NotNull @PositiveOrZero Integer latencyMs,
        Boolean hintShown) {
}
