package com.lingualoop.api.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SM-2 style spaced-repetition scheduling.
 *
 * Grade is 0..5 (5 = effortless recall, 0 = complete blackout). Grades >= 3
 * count as successful recalls.
 *
 * - First review of an exercise:
 *   - grade >= 3: interval 1 day, repetitions = 1.
 *   - grade <  3: interval 0 (due again immediately), lapses = 1.
 * - Successful review of a known exercise: interval grows per SM-2
 *   (1 day, then 6 days, then interval * ease factor), ease is adjusted
 *   towards the difficulty implied by the grade.
 * - Failed review: repetitions reset to 0, lapses incremented, ease drops,
 *   the exercise is due again immediately.
 */
public final class Sm2Scheduler {

    public static final double INITIAL_EASE = 2.5;
    public static final double MIN_EASE = 1.3;

    public record Outcome(double easeFactor, double intervalDays, int repetitions, int lapses, Instant dueAt) {
    }

    private Sm2Scheduler() {
    }

    public static Outcome firstReview(int grade, Instant now) {
        if (grade >= 3) {
            return new Outcome(INITIAL_EASE, 1.0, 1, 0, now.plus(1, ChronoUnit.DAYS));
        }
        return new Outcome(INITIAL_EASE, 0.0, 0, 1, now);
    }

    public static Outcome review(double easeFactor, double intervalDays, int repetitions, int lapses, int grade,
            Instant now) {
        if (grade >= 3) {
            int newRepetitions = repetitions + 1;
            double newInterval;
            if (newRepetitions == 1) {
                newInterval = 1.0;
            } else if (newRepetitions == 2) {
                newInterval = 6.0;
            } else {
                newInterval = Math.max(1.0, Math.round(intervalDays * easeFactor));
            }
            double newEase = Math.max(MIN_EASE,
                    easeFactor + (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02)));
            return new Outcome(newEase, newInterval, newRepetitions, lapses, now.plus((long) newInterval, ChronoUnit.DAYS));
        }
        double newEase = Math.max(MIN_EASE, easeFactor - 0.2);
        return new Outcome(newEase, 0.0, 0, lapses + 1, now);
    }
}
