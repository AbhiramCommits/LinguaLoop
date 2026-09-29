package com.lingualoop.api.learner.dto;

public record AttemptResultDto(Long attemptId, Long exerciseId, int grade, ReviewInfoDto review) {
}
