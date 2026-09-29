package com.lingualoop.api.content.dto;

import java.util.List;

import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.content.ExerciseType;

public record ExerciseDto(Long id, ExerciseType type, String prompt, String answer, List<String> choices,
        String caption, AudioAssetDto audioAsset) {
}
