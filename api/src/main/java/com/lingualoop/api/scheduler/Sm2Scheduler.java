package com.lingualoop.api.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.lingualoop.api.learner.ReviewState;

/**
 * Pure SM-2 spaced-repetition scheduler. Dependency-free: no Spring
 * annotations, no persistence access. The input state is never mutated; a
 * new {@link ReviewState} instance carrying the updated scheduling values is
 * returned.
 *
 * Rules:
 * <ul>
 *   <li>{@code grade < 3} (failed recall): repetitions reset to 0,
 *       intervalDays = 1, lapses incremented, ease factor floored at 1.3.</li>
 *   <li>{@code grade >= 3} (successful recall): repetitions incremented;
 *       interval 1 day after the first success, 6 days after the second,
 *       then {@code round(intervalDays * easeFactor)}; ease factor updated as
 *       {@code EF' = EF + (0.1 - (5-g) * (0.08 + (5-g) * 0.02))} and clamped
 *       to [1.3, 2.5].</li>
 * </ul>
 * dueAt is anchored to the start of the learner's local day: the local date
 * of {@code now} in the learner's timezone plus {@code intervalDays} days,
 * at 00:00 in that timezone.
 */
public final class Sm2Scheduler {

    public static final double MIN_EASE_FACTOR = 1.3;
    public static final double MAX_EASE_FACTOR = 2.5;
    public static final double DEFAULT_EASE_FACTOR = 2.5;

    private Sm2Scheduler() {
    }

    public static ReviewState schedule(ReviewState current, int grade, ZoneId timezone, Instant now) {
        if (grade < 0 || grade > 5) {
            throw new IllegalArgumentException("grade must be between 0 and 5, got " + grade);
        }

        double easeFactor = current.getEaseFactor();
        double intervalDays;
        int repetitions;
        int lapses = current.getLapses();

        if (grade < 3) {
            repetitions = 0;
            intervalDays = 1.0;
            lapses = lapses + 1;
            easeFactor = Math.max(MIN_EASE_FACTOR, easeFactor);
        } else {
            repetitions = current.getRepetitions() + 1;
            easeFactor = clampEase(easeFactor + (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02)));
            if (repetitions == 1) {
                intervalDays = 1.0;
            } else if (repetitions == 2) {
                intervalDays = 6.0;
            } else {
                intervalDays = Math.round(current.getIntervalDays() * easeFactor);
            }
        }

        ReviewState next = new ReviewState(current.getLearner(), current.getExercise(),
                nextDueAt(now, timezone, intervalDays));
        next.setEaseFactor(easeFactor);
        next.setIntervalDays(intervalDays);
        next.setRepetitions(repetitions);
        next.setLapses(lapses);
        next.setLastGrade((short) grade);
        return next;
    }

    public static Instant nextDueAt(Instant now, ZoneId timezone, double intervalDays) {
        LocalDate today = now.atZone(timezone).toLocalDate();
        return today.plusDays((long) intervalDays).atStartOfDay(timezone).toInstant();
    }

    private static double clampEase(double easeFactor) {
        return Math.min(MAX_EASE_FACTOR, Math.max(MIN_EASE_FACTOR, easeFactor));
    }
}
