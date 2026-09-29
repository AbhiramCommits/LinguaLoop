package com.lingualoop.api.auth.dto;

public record AuthResponse(String token, LearnerDto learner) {
}
