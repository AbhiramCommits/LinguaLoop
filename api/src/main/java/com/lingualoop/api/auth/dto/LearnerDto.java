package com.lingualoop.api.auth.dto;

import com.lingualoop.api.learner.Learner;

public record LearnerDto(Long id, String email, String displayName, String timezone) {

    public static LearnerDto from(Learner learner) {
        return new LearnerDto(learner.getId(), learner.getEmail(), learner.getDisplayName(), learner.getTimezone());
    }
}
