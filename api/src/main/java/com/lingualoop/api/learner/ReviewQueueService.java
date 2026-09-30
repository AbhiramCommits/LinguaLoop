package com.lingualoop.api.learner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.common.config.QueueProperties;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.Unit;
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
 * Builds the per-learner review queue.
 *
 * The queue is due review_state rows (due_at <= now, ordered by due_at),
 * capped at a configurable daily limit, backfilled with never-seen exercises
 * from the learner's active unit. PostgreSQL is the source of truth; the
 * assembled queue is cached in Redis under {@code queue:{learnerId}} (TTL
 * from config, default 15 minutes) purely as a read accelerator. Every
 * attempt evicts the cache, and a cold or unavailable Redis still yields a
 * correct queue straight from Postgres.
 */
@Service
public class ReviewQueueService {

    private static final Logger log = LoggerFactory.getLogger(ReviewQueueService.class);

    private final ReviewStateRepository reviewStates;
    private final ExerciseRepository exercises;
    private final ActiveUnitResolver activeUnitResolver;
    private final QueueProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ReviewQueueService(ReviewStateRepository reviewStates, ExerciseRepository exercises,
            ActiveUnitResolver activeUnitResolver, QueueProperties properties, StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.reviewStates = reviewStates;
        this.exercises = exercises;
        this.activeUnitResolver = activeUnitResolver;
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

        List<ReviewState> dueStates = reviewStates.findDueWithDetails(learnerId, now,
                PageRequest.of(0, properties.limit()));
        List<QueueItemDto> items = new ArrayList<>(dueStates.stream().map(this::toDueItem).toList());

        int remaining = properties.limit() - items.size();
        if (remaining > 0) {
            activeUnitResolver.resolve(learnerId).ifPresent(unit ->
                    exercises.findUnseenInUnit(unit.getId(), learnerId, PageRequest.of(0, remaining))
                            .forEach(exercise -> items.add(toNewItem(exercise, unit))));
        }

        QueueResponse response = new QueueResponse(items, dueStates.size());
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

    private QueueItemDto toDueItem(ReviewState state) {
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
                        state.getDueAt(), state.getLastGrade(), state.getLapses()),
                false);
    }

    private QueueItemDto toNewItem(Exercise exercise, Unit unit) {
        return new QueueItemDto(
                exercise.getId(),
                exercise.getType(),
                exercise.getPrompt(),
                exercise.getAnswer(),
                exercise.getChoices(),
                exercise.getCaption(),
                exercise.getAudioAsset() == null ? null : AudioAssetDto.from(exercise.getAudioAsset()),
                exercise.getLesson().getId(),
                exercise.getLesson().getTitle(),
                unit.getId(),
                unit.getTitle(),
                null,
                true);
    }

    private String cacheKey(Long learnerId) {
        return "queue:" + learnerId;
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
