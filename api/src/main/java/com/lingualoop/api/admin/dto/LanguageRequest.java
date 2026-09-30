package com.lingualoop.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LanguageRequest(
        @NotBlank @Pattern(regexp = "[a-z]{2,3}(-[A-Z]{2})?", message = "language code must look like 'es' or 'pt-BR'") String code,
        @NotBlank @Size(max = 100) String name) {
}
