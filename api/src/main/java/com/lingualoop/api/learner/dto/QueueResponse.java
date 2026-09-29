package com.lingualoop.api.learner.dto;

import java.util.List;

public record QueueResponse(List<QueueItemDto> items, int dueCount) {
}
