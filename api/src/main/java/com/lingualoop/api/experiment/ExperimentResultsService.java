package com.lingualoop.api.experiment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lingualoop.api.common.error.ConflictException;
import com.lingualoop.api.common.error.NotFoundException;
import com.lingualoop.api.experiment.dto.ExperimentResultsDto;
import com.lingualoop.api.learner.AttemptRepository;
import com.lingualoop.api.learner.StudySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention metrics computed from real session/attempt data.
 *
 * Per variant:
 * - D1/D7 return rate: fraction of learners (with >= 1 session) who have a
 *   session on their first-session day +1 / +7 (UTC dates).
 * - Mean accuracy on the second exposure to an exercise (grade 0..5).
 * - Mean items (graded attempts) completed per session.
 *
 * P-values are two-tailed two-proportion z-tests against the control variant
 * for the return-rate metrics. Results are guarded: DRAFT experiments expose
 * no results, and groups below the minimum size are flagged notEnoughData.
 */
@Service
public class ExperimentResultsService {

    static final long MIN_GROUP_SIZE = 30;

    private final ExperimentRepository experiments;
    private final VariantRepository variants;
    private final AssignmentRepository assignments;
    private final StudySessionRepository sessions;
    private final AttemptRepository attempts;

    public ExperimentResultsService(ExperimentRepository experiments, VariantRepository variants,
            AssignmentRepository assignments, StudySessionRepository sessions, AttemptRepository attempts) {
        this.experiments = experiments;
        this.variants = variants;
        this.assignments = assignments;
        this.sessions = sessions;
        this.attempts = attempts;
    }

    private record Metrics(long n, Double d1Rate, Double d7Rate, Double meanSecondAccuracy,
            Double meanItemsPerSession) {
        static Metrics empty() {
            return new Metrics(0, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public ExperimentResultsDto results(String experimentKey, boolean includeSimulated) {
        Experiment experiment = experiments.findById(experimentKey)
                .orElseThrow(() -> new NotFoundException("Unknown experiment: " + experimentKey));
        if (experiment.getStatus() == ExperimentStatus.DRAFT) {
            throw new ConflictException("Results are not available for a DRAFT experiment");
        }

        List<Variant> variantList = variants.findByExperimentKeyOrderByIdAsc(experimentKey);
        Variant control = variantList.stream().filter(Variant::isControl).findFirst()
                .orElseThrow(() -> new ConflictException("Experiment " + experimentKey + " has no control variant"));

        Map<String, List<Long>> learnerIdsPerVariant = new LinkedHashMap<>();
        for (Variant variant : variantList) {
            learnerIdsPerVariant.put(variant.getKey(), includeSimulated
                    ? assignments.findLearnerIds(experimentKey, variant.getKey())
                    : assignments.findRealLearnerIds(experimentKey, variant.getKey()));
        }

        Metrics controlMetrics = computeMetrics(learnerIdsPerVariant.getOrDefault(control.getKey(), List.of()));

        List<ExperimentResultsDto.VariantResultsDto> rows = new ArrayList<>();
        for (Variant variant : variantList) {
            List<Long> learnerIds = learnerIdsPerVariant.getOrDefault(variant.getKey(), List.of());
            Metrics metrics = computeMetrics(learnerIds);
            boolean enoughData = metrics.n() >= MIN_GROUP_SIZE;
            Double d1PValue = null;
            Double d7PValue = null;
            if (!variant.isControl() && enoughData && controlMetrics.n() >= MIN_GROUP_SIZE) {
                d1PValue = ZTest.twoProportionPValue(metrics.d1Rate(), metrics.n(),
                        controlMetrics.d1Rate(), controlMetrics.n());
                d7PValue = ZTest.twoProportionPValue(metrics.d7Rate(), metrics.n(),
                        controlMetrics.d7Rate(), controlMetrics.n());
            }
            rows.add(new ExperimentResultsDto.VariantResultsDto(
                    variant.getKey(), variant.isControl(), metrics.n(), enoughData,
                    metrics.d1Rate(), d1PValue, metrics.d7Rate(), d7PValue,
                    metrics.meanSecondAccuracy(), metrics.meanItemsPerSession()));
        }

        return new ExperimentResultsDto(experimentKey, experiment.getStatus().name(), control.getKey(),
                includeSimulated, rows);
    }

    private Metrics computeMetrics(List<Long> learnerIds) {
        if (learnerIds.isEmpty()) {
            return Metrics.empty();
        }

        Map<Long, LocalDate> firstSessionDay = new HashMap<>();
        Map<Long, Set<LocalDate>> activeDays = new HashMap<>();
        for (Object[] row : sessions.findStartedAtByLearnerIds(learnerIds)) {
            long learnerId = (Long) row[0];
            LocalDate day = ((Instant) row[1]).atZone(ZoneOffset.UTC).toLocalDate();
            firstSessionDay.merge(learnerId, day, (a, b) -> a.isBefore(b) ? a : b);
            activeDays.computeIfAbsent(learnerId, ignored -> new HashSet<>()).add(day);
        }
        long n = firstSessionDay.size();
        long d1 = 0;
        long d7 = 0;
        for (Map.Entry<Long, LocalDate> entry : firstSessionDay.entrySet()) {
            Set<LocalDate> days = activeDays.get(entry.getKey());
            if (days.contains(entry.getValue().plusDays(1))) {
                d1++;
            }
            if (days.contains(entry.getValue().plusDays(7))) {
                d7++;
            }
        }

        Double meanSecondAccuracy = meanSecondAttemptAccuracy(attempts.findAttemptHistoryByLearnerIds(learnerIds));
        Double meanItemsPerSession = meanItemsPerSession(
                attempts.countAttemptsPerSessionByLearnerIds(learnerIds));

        return new Metrics(n,
                d1 / (double) n,
                d7 / (double) n,
                meanSecondAccuracy,
                meanItemsPerSession);
    }

    private static Double meanSecondAttemptAccuracy(List<Object[]> attemptHistory) {
        Map<Long, Map<Long, List<Short>>> grades = new HashMap<>();
        for (Object[] row : attemptHistory) {
            long learnerId = (Long) row[0];
            long exerciseId = (Long) row[1];
            short grade = (Short) row[2];
            grades.computeIfAbsent(learnerId, ignored -> new HashMap<>())
                    .computeIfAbsent(exerciseId, ignored -> new ArrayList<>())
                    .add(grade);
        }
        double total = 0;
        long learners = 0;
        for (Map<Long, List<Short>> learnerGrades : grades.values()) {
            double learnerTotal = 0;
            long exercises = 0;
            for (List<Short> history : learnerGrades.values()) {
                if (history.size() >= 2) {
                    learnerTotal += history.get(1);
                    exercises++;
                }
            }
            if (exercises > 0) {
                total += learnerTotal / exercises;
                learners++;
            }
        }
        return learners == 0 ? null : round2(total / learners);
    }

    private static Double meanItemsPerSession(List<Object[]> sessionCounts) {
        Map<Long, List<Long>> counts = new HashMap<>();
        for (Object[] row : sessionCounts) {
            long learnerId = (Long) row[0];
            counts.computeIfAbsent(learnerId, ignored -> new ArrayList<>()).add((Long) row[1]);
        }
        double total = 0;
        for (List<Long> learnerCounts : counts.values()) {
            total += learnerCounts.stream().mapToLong(Long::longValue).average().orElse(0);
        }
        return counts.isEmpty() ? null : round2(total / counts.size());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
