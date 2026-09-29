package com.lingualoop.api.content.dto;

import java.util.List;

public record LessonDto(Long id, Long unitId, String title, int position, List<ExerciseDto> exercises) {
}
