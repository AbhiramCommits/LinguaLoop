package com.lingualoop.api.content.dto;

import java.util.List;

public record UnitDto(Long id, Long languageId, String title, int position, List<LessonSummary> lessons) {

    public record LessonSummary(Long id, String title, int position, long exerciseCount) {
    }
}
