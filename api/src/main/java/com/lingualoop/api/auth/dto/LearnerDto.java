package com.lingualoop.api.auth.dto;

import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.LearnerRole;

public record LearnerDto(Long id, String email, String displayName, String timezone, LearnerRole role) {

    public static LearnerDto from(Learner learner) {
        return new LearnerDto(learner.getId(), learner.getEmail(), learner.getDisplayName(),
                learner.getTimezone(), learner.getRole());
    }
}
