package com.lingualoop.api.learner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lingualoop.api.audio.AudioAssetDto;
import com.lingualoop.api.common.config.QueueProperties;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.experiment.ExperimentClient;
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
    private final ExperimentClient experimentClient;

    public ReviewQueueService(ReviewStateRepository reviewStates, ExerciseRepository exercises,
            ActiveUnitResolver activeUnitResolver, QueueProperties properties, StringRedisTemplate redis,
            ObjectMapper objectMapper, ExperimentClient experimentClient) {
        this.reviewStates = reviewStates;
        this.exercises = exercises;
        this.activeUnitResolver = activeUnitResolver;
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.experimentClient = experimentClient;
    }

    /**
     * Read-write: the first queue read is the first exposure to the
     * lesson_ordering experiment, which persists the learner's assignment.
     */
    @Transactional
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
        List<QueueItemDto> dueItems = dueStates.stream().map(this::toDueItem).toList();

        int remaining = properties.limit() - dueItems.size();
        List<QueueItemDto> newItems = new ArrayList<>();
        if (remaining > 0) {
            activeUnitResolver.resolve(learnerId).ifPresent(unit ->
                    exercises.findUnseenInUnit(unit.getId(), learnerId, PageRequest.of(0, remaining))
                            .forEach(exercise -> newItems.add(toNewItem(exercise, unit))));
        }

        List<QueueItemDto> items = orderForVariant(learnerId, dueItems, newItems);

        QueueResponse response = new QueueResponse(items, dueStates.size());
        writeCache(key, response);
        return response;
    }

    /**
     * lesson_ordering experiment: control ("due_first") keeps strict SM-2 due
     * order followed by new items; "interleaved" mixes due and new items at
     * the configured ratio (config.ratio = share of new items). Degrades to
     * due-first when one of the lists is empty.
     */
    private List<QueueItemDto> orderForVariant(Long learnerId, List<QueueItemDto> dueItems,
            List<QueueItemDto> newItems) {
        double ratio = 0.0;
        try {
            InterleaveConfig config = experimentClient.configFor(learnerId, "lesson_ordering",
                    InterleaveConfig.class);
            if (config != null && config.ratio() > 0) {
                ratio = config.ratio();
            }
        } catch (RuntimeException ex) {
            log.debug("lesson_ordering experiment unavailable; falling back to due-first", ex);
        }
        if (ratio <= 0 || dueItems.isEmpty() || newItems.isEmpty()) {
            List<QueueItemDto> combined = new ArrayList<>(dueItems.size() + newItems.size());
            combined.addAll(dueItems);
            combined.addAll(newItems);
            return combined;
        }

        int duePerNew = Math.max(1, (int) Math.round((1.0 - ratio) / ratio));
        List<QueueItemDto> interleaved = new ArrayList<>(dueItems.size() + newItems.size());
        int dueIndex = 0;
        int newIndex = 0;
        while (dueIndex < dueItems.size() || newIndex < newItems.size()) {
            for (int i = 0; i < duePerNew && dueIndex < dueItems.size(); i++) {
                interleaved.add(dueItems.get(dueIndex++));
            }
            if (newIndex < newItems.size()) {
                interleaved.add(newItems.get(newIndex++));
            }
        }
        return interleaved.subList(0, Math.min(interleaved.size(), properties.limit()));
    }

    public record InterleaveConfig(double ratio) {
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
