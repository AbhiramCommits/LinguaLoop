package com.lingualoop.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;

import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseType;
import com.lingualoop.api.content.Language;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.ReviewState;
import org.junit.jupiter.api.Test;

class Sm2SchedulerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void firstSuccessSchedulesOneDay() {
        // EF' = 2.5 + (0.1 - 0) = 2.6 -> clamped to 2.5
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 0.0, 0, 0, UTC), 5, UTC, NOW);
        assertThat(next.getRepetitions()).isEqualTo(1);
        assertThat(next.getIntervalDays()).isEqualTo(1.0);
        assertThat(next.getLapses()).isZero();
        assertThat(next.getEaseFactor()).isEqualTo(2.5);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
    }

    @Test
    void secondSuccessSchedulesSixDays() {
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 1.0, 1, 0, UTC), 5, UTC, NOW);
        assertThat(next.getRepetitions()).isEqualTo(2);
        assertThat(next.getIntervalDays()).isEqualTo(6.0);
        assertThat(next.getEaseFactor()).isEqualTo(2.5);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
    }

    @Test
    void laterSuccessMultipliesIntervalByEase() {
        // round(6 * 2.5) = 15
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 6.0, 2, 0, UTC), 5, UTC, NOW);
        assertThat(next.getRepetitions()).isEqualTo(3);
        assertThat(next.getIntervalDays()).isEqualTo(15.0);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-10-14T00:00:00Z"));
    }

    @Test
    void gradeFourLeavesEaseUnchanged() {
        // EF' = 2.36 + (0.1 - 1*(0.08 + 1*0.02)) = 2.36 + 0.0 = 2.36
        // round(15 * 2.36) = round(35.4) = 35
        ReviewState next = Sm2Scheduler.schedule(state(2.36, 15.0, 3, 0, UTC), 4, UTC, NOW);
        assertThat(next.getEaseFactor()).isCloseTo(2.36, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(next.getIntervalDays()).isEqualTo(35.0);
        assertThat(next.getRepetitions()).isEqualTo(4);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-11-03T00:00:00Z"));
    }

    @Test
    void gradeThreeLowersEaseByZeroPointOneFour() {
        // EF' = 2.5 + (0.1 - 2*(0.08 + 2*0.02)) = 2.5 + (0.1 - 0.24) = 2.36
        // round(6 * 2.36) = round(14.16) = 14
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 6.0, 2, 0, UTC), 3, UTC, NOW);
        assertThat(next.getEaseFactor()).isCloseTo(2.36, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(next.getIntervalDays()).isEqualTo(14.0);
        assertThat(next.getRepetitions()).isEqualTo(3);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
    }

    @Test
    void easeIsClampedAtMaximum() {
        // 2.45 + 0.1 = 2.55 -> clamped to 2.5; round(10 * 2.5) = 25
        ReviewState next = Sm2Scheduler.schedule(state(2.45, 10.0, 4, 0, UTC), 5, UTC, NOW);
        assertThat(next.getEaseFactor()).isEqualTo(2.5);
        assertThat(next.getIntervalDays()).isEqualTo(25.0);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
    }

    @Test
    void easeIsClampedAtMinimumOnSuccess() {
        // 1.31 - 0.14 = 1.17 -> clamped to 1.3; round(10 * 1.3) = 13
        ReviewState next = Sm2Scheduler.schedule(state(1.31, 10.0, 4, 0, UTC), 3, UTC, NOW);
        assertThat(next.getEaseFactor()).isEqualTo(1.3);
        assertThat(next.getIntervalDays()).isEqualTo(13.0);
        assertThat(next.getRepetitions()).isEqualTo(5);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
    }

    @Test
    void gradeTwoFailureResetsRepetitionsAndIncrementsLapses() {
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 15.0, 4, 1, UTC), 2, UTC, NOW);
        assertThat(next.getRepetitions()).isZero();
        assertThat(next.getIntervalDays()).isEqualTo(1.0);
        assertThat(next.getLapses()).isEqualTo(2);
        assertThat(next.getEaseFactor()).isEqualTo(2.5);
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
    }

    @Test
    void failureFloorsEaseAtMinimum() {
        ReviewState next = Sm2Scheduler.schedule(state(1.2, 15.0, 4, 0, UTC), 0, UTC, NOW);
        assertThat(next.getEaseFactor()).isEqualTo(1.3);
        assertThat(next.getRepetitions()).isZero();
        assertThat(next.getIntervalDays()).isEqualTo(1.0);
        assertThat(next.getLapses()).isEqualTo(1);
    }

    @Test
    void failureAtFloorKeepsEaseAtFloor() {
        ReviewState next = Sm2Scheduler.schedule(state(1.3, 15.0, 4, 2, UTC), 1, UTC, NOW);
        assertThat(next.getEaseFactor()).isEqualTo(1.3);
        assertThat(next.getLapses()).isEqualTo(3);
    }

    @Test
    void dueAtIsAnchoredToTheLearnersLocalDay() {
        ZoneId newYork = ZoneId.of("America/New_York");
        // 2026-09-30T01:00Z = 2026-09-29 21:00 EDT -> local today is Sep 29,
        // so interval 1 lands at 2026-09-30T00:00 EDT = 2026-09-30T04:00Z.
        ReviewState next = Sm2Scheduler.schedule(state(2.5, 0.0, 0, 0, newYork), 5, newYork,
                Instant.parse("2026-09-30T01:00:00Z"));
        assertThat(next.getDueAt()).isEqualTo(Instant.parse("2026-09-30T04:00:00Z"));

        // Crossing the local day boundary: 2026-09-30T04:30Z = 2026-09-30 00:30 EDT
        // -> local today is Sep 30, due date becomes Oct 1 00:00 EDT.
        ReviewState nextDay = Sm2Scheduler.schedule(state(2.5, 0.0, 0, 0, newYork), 5, newYork,
                Instant.parse("2026-09-30T04:30:00Z"));
        assertThat(nextDay.getDueAt()).isEqualTo(Instant.parse("2026-10-01T04:00:00Z"));
    }

    @Test
    void inputStateIsNeverMutated() {
        ReviewState current = state(2.5, 15.0, 4, 1, UTC);
        Sm2Scheduler.schedule(current, 2, UTC, NOW);
        assertThat(current.getRepetitions()).isEqualTo(4);
        assertThat(current.getIntervalDays()).isEqualTo(15.0);
        assertThat(current.getLapses()).isEqualTo(1);
        assertThat(current.getEaseFactor()).isEqualTo(2.5);
    }

    @Test
    void invalidGradesAreRejected() {
        ReviewState current = state(2.5, 1.0, 1, 0, UTC);
        assertThatThrownBy(() -> Sm2Scheduler.schedule(current, 6, UTC, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sm2Scheduler.schedule(current, -1, UTC, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ReviewState state(double easeFactor, double intervalDays, int repetitions, int lapses,
            ZoneId timezone) {
        Language language = new Language("es", "Spanish");
        Unit unit = new Unit(language, "Unidad", 1);
        Lesson lesson = new Lesson(unit, "Lección", 1);
        Exercise exercise = new Exercise(lesson, ExerciseType.TRANSLATE, "prompt", "answer");
        Learner learner = new Learner("learner@example.com", "Learner", "hash", timezone.getId());
        ReviewState state = new ReviewState(learner, exercise, Instant.EPOCH);
        state.setEaseFactor(easeFactor);
        state.setIntervalDays(intervalDays);
        state.setRepetitions(repetitions);
        state.setLapses(lapses);
        return state;
    }
}
