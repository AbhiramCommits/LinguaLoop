package com.lingualoop.api.experiment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lingualoop.api.common.error.ConflictException;
import com.lingualoop.api.common.error.NotFoundException;
import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.LearnerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Learner-facing experiment client.
 *
 * Assignment resolution is DB-first: a persisted {@link Assignment} always
 * wins, so learners are never reshuffled when weights or variants change.
 * First exposure falls back to deterministic SHA-256 bucketing
 * ({@link DeterministicBucketer}) and persists the result. The resolved
 * variant is cached in Redis ({@code assignment:{learnerId}:{experimentKey}})
 * as a read accelerator only.
 *
 * Exposure semantics: DRAFT experiments never assign; STOPPED experiments
 * serve persisted assignments but take no new ones; RUNNING assigns.
 */
@Service
public class ExperimentClient {

    private static final Logger log = LoggerFactory.getLogger(ExperimentClient.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ExperimentRepository experiments;
    private final VariantRepository variants;
    private final AssignmentRepository assignments;
    private final LearnerRepository learners;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ExperimentClient(ExperimentRepository experiments, VariantRepository variants,
            AssignmentRepository assignments, LearnerRepository learners, StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.experiments = experiments;
        this.variants = variants;
        this.assignments = assignments;
        this.learners = learners;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public record ExperimentVariant(String experimentKey, String variantKey, Map<String, Object> config,
            boolean control) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExperimentVariant variantFor(long learnerId, String experimentKey) {
        Experiment experiment = experiments.findById(experimentKey)
                .orElseThrow(() -> new NotFoundException("Unknown experiment: " + experimentKey));

        ExperimentVariant cached = readCache(learnerId, experimentKey);
        if (cached != null) {
            return cached;
        }

        Assignment persisted = assignments.findByLearnerIdAndExperimentKey(learnerId, experimentKey).orElse(null);
        if (persisted != null) {
            return toVariant(experiment, persisted.getVariantKey());
        }

        if (experiment.getStatus() == ExperimentStatus.DRAFT) {
            throw new ConflictException("Experiment " + experimentKey + " is in DRAFT and not exposed to learners");
        }
        if (experiment.getStatus() == ExperimentStatus.STOPPED) {
            throw new ConflictException("Experiment " + experimentKey + " is stopped and no longer assigns learners");
        }

        List<Variant> available = variants.findByExperimentKeyOrderByIdAsc(experimentKey);
        if (available.isEmpty()) {
            throw new ConflictException("Experiment " + experimentKey + " has no variants");
        }
        int index = DeterministicBucketer.selectVariant(
                DeterministicBucketer.uniform01(experimentKey, learnerId),
                available.stream().map(Variant::getWeight).toList());
        Variant picked = available.get(index);

        Learner learner = learners.findById(learnerId)
                .orElseThrow(() -> new NotFoundException("Learner " + learnerId + " not found"));
        try {
            assignments.saveAndFlush(new Assignment(learner, experimentKey, picked.getKey()));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent first exposure: someone else persisted first — theirs wins.
            Assignment winner = assignments.findByLearnerIdAndExperimentKey(learnerId, experimentKey)
                    .orElseThrow(() -> new IllegalStateException("Assignment lost after race", ex));
            return toVariant(experiment, winner.getVariantKey());
        }

        ExperimentVariant result = toVariant(experiment, picked.getKey());
        writeCache(learnerId, experimentKey, result);
        return result;
    }

    /** Typed accessor for a variant's JSONB config. Returns null when the config is absent. */
    public <T> T configFor(long learnerId, String experimentKey, Class<T> type) {
        ExperimentVariant variant = variantFor(learnerId, experimentKey);
        if (variant.config() == null || variant.config().isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(variant.config(), type);
    }

    private ExperimentVariant toVariant(Experiment experiment, String variantKey) {
        Variant variant = variants.findByExperimentKeyAndKey(experiment.getKey(), variantKey)
                .orElseThrow(() -> new NotFoundException(
                        "Variant " + variantKey + " of experiment " + experiment.getKey() + " not found"));
        return new ExperimentVariant(experiment.getKey(), variant.getKey(),
                variant.getConfig() == null ? new LinkedHashMap<>() : variant.getConfig(), variant.isControl());
    }

    private String cacheKey(long learnerId, String experimentKey) {
        return "assignment:" + learnerId + ":" + experimentKey;
    }

    private ExperimentVariant readCache(long learnerId, String experimentKey) {
        try {
            String json = redis.opsForValue().get(cacheKey(learnerId, experimentKey));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Redis unavailable or cache unreadable; falling back to the database", ex);
            return null;
        }
    }

    private void writeCache(long learnerId, String experimentKey, ExperimentVariant variant) {
        try {
            redis.opsForValue().set(cacheKey(learnerId, experimentKey),
                    objectMapper.writeValueAsString(variant), CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Redis unavailable while caching assignment", ex);
        }
    }
}
