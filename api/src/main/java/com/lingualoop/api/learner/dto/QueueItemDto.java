package com.lingualoop.api.learner.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.content.ExerciseType;

public record QueueItemDto(Long exerciseId, ExerciseType type, String prompt, String answer, List<String> choices,
        String caption, AudioAssetDto audioAsset, Long lessonId, String lessonTitle, Long unitId, String unitTitle,
        ReviewInfoDto review, @JsonProperty("new") boolean isNew) {
}
