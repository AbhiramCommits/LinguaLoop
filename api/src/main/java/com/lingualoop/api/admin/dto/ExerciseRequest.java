package com.lingualoop.api.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExerciseRequest(
        @NotNull Long lessonId,
        @NotBlank String type,
        @NotBlank @Size(max = 2000) String prompt,
        @NotBlank @Size(max = 2000) String answer,
        List<String> choices,
        @Size(max = 2000) String caption) {
}
