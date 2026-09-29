package com.lingualoop.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class Sm2SchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void firstReviewSuccessStartsAtOneDay() {
        var outcome = Sm2Scheduler.firstReview(5, NOW);
        assertThat(outcome.repetitions()).isEqualTo(1);
        assertThat(outcome.intervalDays()).isEqualTo(1.0);
        assertThat(outcome.lapses()).isZero();
        assertThat(outcome.easeFactor()).isEqualTo(2.5);
        assertThat(outcome.dueAt()).isEqualTo(NOW.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void firstReviewFailureIsDueImmediately() {
        var outcome = Sm2Scheduler.firstReview(1, NOW);
        assertThat(outcome.repetitions()).isZero();
        assertThat(outcome.intervalDays()).isZero();
        assertThat(outcome.lapses()).isEqualTo(1);
        assertThat(outcome.dueAt()).isEqualTo(NOW);
    }

    @Test
    void secondSuccessfulReviewMovesToSixDays() {
        var outcome = Sm2Scheduler.review(2.5, 1.0, 1, 0, 5, NOW);
        assertThat(outcome.repetitions()).isEqualTo(2);
        assertThat(outcome.intervalDays()).isEqualTo(6.0);
        assertThat(outcome.dueAt()).isEqualTo(NOW.plus(6, ChronoUnit.DAYS));
    }

    @Test
    void intervalGrowsByEaseFactorAfterThirdReview() {
        var outcome = Sm2Scheduler.review(2.5, 6.0, 2, 0, 5, NOW);
        assertThat(outcome.repetitions()).isEqualTo(3);
        assertThat(outcome.intervalDays()).isEqualTo(15.0);
        assertThat(outcome.dueAt()).isEqualTo(NOW.plus(15, ChronoUnit.DAYS));
    }

    @Test
    void perfectGradeRaisesEase() {
        var outcome = Sm2Scheduler.review(2.5, 6.0, 2, 0, 5, NOW);
        assertThat(outcome.easeFactor()).isCloseTo(2.6, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void marginalGradeLowersEase() {
        var outcome = Sm2Scheduler.review(2.5, 6.0, 2, 0, 3, NOW);
        assertThat(outcome.easeFactor()).isCloseTo(2.36, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void failureResetsRepetitionsAndIncrementsLapses() {
        var outcome = Sm2Scheduler.review(2.5, 15.0, 4, 2, 1, NOW);
        assertThat(outcome.repetitions()).isZero();
        assertThat(outcome.intervalDays()).isZero();
        assertThat(outcome.lapses()).isEqualTo(3);
        assertThat(outcome.easeFactor()).isCloseTo(2.3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(outcome.dueAt()).isEqualTo(NOW);
    }

    @Test
    void easeNeverDropsBelowFloor() {
        var outcome = Sm2Scheduler.review(1.31, 6.0, 2, 0, 0, NOW);
        assertThat(outcome.easeFactor()).isEqualTo(1.3);
    }
}
