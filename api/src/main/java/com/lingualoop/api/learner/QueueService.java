package com.lingualoop.api.learner;

import java.time.Instant;
import java.util.List;

import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.common.config.QueueProperties;
import com.lingualoop.api.learner.dto.QueueItemDto;
import com.lingualoop.api.learner.dto.QueueResponse;
import com.lingualoop.api.learner.dto.ReviewInfoDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The per-learner review queue is computed from review_state in PostgreSQL
 * (the source of truth) and cached in Redis. Attempts invalidate the cache.
 * Redis failures degrade gracefully to direct database reads.
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ReviewStateRepository reviewStates;
    private final QueueProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public QueueService(ReviewStateRepository reviewStates, QueueProperties properties,
            StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.reviewStates = reviewStates;
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public QueueResponse getQueue(Long learnerId, Instant now) {
        String key = cacheKey(learnerId);
        String cached = readCache(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, QueueResponse.class);
            } catch (JsonProcessingException ex) {
                log.debug("Discarding unreadable queue cache entry {}", key, ex);
            }
        }

        List<ReviewState> due = reviewStates.findDueWithDetails(learnerId, now,
                PageRequest.of(0, properties.limit()));
        List<QueueItemDto> items = due.stream().map(this::toItem).toList();
        QueueResponse response = new QueueResponse(items, items.size());
        writeCache(key, response);
        return response;
    }

    public void evict(Long learnerId) {
        try {
            redis.delete(cacheKey(learnerId));
        } catch (RuntimeException ex) {
            log.debug("Redis unavailable while evicting queue cache", ex);
        }
    }

    private QueueItemDto toItem(ReviewState state) {
        var exercise = state.getExercise();
        var lesson = exercise.getLesson();
        var unit = lesson.getUnit();
        return new QueueItemDto(
                exercise.getId(),
                exercise.getType(),
                exercise.getPrompt(),
                exercise.getAnswer(),
                exercise.getChoices(),
                exercise.getCaption(),
                exercise.getAudioAsset() == null ? null : AudioAssetDto.from(exercise.getAudioAsset()),
                lesson.getId(),
                lesson.getTitle(),
                unit.getId(),
                unit.getTitle(),
                new ReviewInfoDto(state.getEaseFactor(), state.getIntervalDays(), state.getRepetitions(),
                        state.getDueAt(), state.getLastGrade(), state.getLapses()));
    }

    private String cacheKey(Long learnerId) {
        return "review-queue:" + learnerId;
    }

    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.debug("Redis unavailable while reading queue cache", ex);
            return null;
        }
    }

    private void writeCache(String key, QueueResponse response) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(response), properties.cacheTtl());
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Redis unavailable while writing queue cache", ex);
        }
    }
}
