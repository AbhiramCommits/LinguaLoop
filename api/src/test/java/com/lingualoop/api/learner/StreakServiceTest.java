package com.lingualoop.api.learner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    private static final Long LEARNER_ID = 1L;

    @Mock
    private StreakRepository streaks;

    @Mock
    private LearnerRepository learners;

    private StreakService service;

    @BeforeEach
    void setUp() {
        service = new StreakService(streaks, learners);
        lenient().when(learners.findById(LEARNER_ID))
                .thenReturn(Optional.of(new Learner("a@b.c", "A", "hash", "America/New_York")));
    }

    @Test
    void firstActivityStartsAStreak() {
        when(streaks.findById(LEARNER_ID)).thenReturn(Optional.empty());
        // 2026-09-30T05:00Z = 2026-09-30 01:00 EDT
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-30T05:00:00Z"));
        Streak saved = captureSaved();
        assertThat(saved.getCurrentDays()).isEqualTo(1);
        assertThat(saved.getLongestDays()).isEqualTo(1);
        assertThat(saved.getLastActiveDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void sameLocalDayDoesNotDoubleIncrement() {
        Streak existing = streak(3, 5, LocalDate.of(2026, 9, 29));
        when(streaks.findById(LEARNER_ID)).thenReturn(Optional.of(existing));
        // 2026-09-29T23:00Z = 2026-09-29 19:00 EDT -> same local day as last activity
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-29T23:00:00Z"));
        Streak saved = captureSaved();
        assertThat(saved.getCurrentDays()).isEqualTo(3);
        assertThat(saved.getLongestDays()).isEqualTo(5);
    }

    @Test
    void consecutiveLocalDayIncrements() {
        Streak existing = streak(3, 5, LocalDate.of(2026, 9, 29));
        when(streaks.findById(LEARNER_ID)).thenReturn(Optional.of(existing));
        // 2026-09-30T05:00Z = 2026-09-30 01:00 EDT -> exactly the next local day
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-30T05:00:00Z"));
        Streak saved = captureSaved();
        assertThat(saved.getCurrentDays()).isEqualTo(4);
        assertThat(saved.getLongestDays()).isEqualTo(5);
    }

    @Test
    void skippedDayResetsCurrentButNeverLowersLongest() {
        Streak existing = streak(7, 9, LocalDate.of(2026, 9, 27));
        when(streaks.findById(LEARNER_ID)).thenReturn(Optional.of(existing));
        // Local day Sep 30: Sep 28 skipped -> current resets, longest stays 9
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-30T05:00:00Z"));
        Streak saved = captureSaved();
        assertThat(saved.getCurrentDays()).isEqualTo(1);
        assertThat(saved.getLongestDays()).isEqualTo(9);
        assertThat(saved.getLastActiveDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void dayBoundaryIsEvaluatedInTheLearnersTimezone() {
        // 2026-09-29T18:00Z = Sep 30 03:00 JST (Tokyo, UTC+9)
        when(learners.findById(2L))
                .thenReturn(Optional.of(new Learner("t@t.jp", "T", "hash", "Asia/Tokyo")));
        when(streaks.findById(2L)).thenReturn(Optional.empty());
        service.recordActivity(2L, Instant.parse("2026-09-29T18:00:00Z"));
        assertThat(captureSaved(2L).getLastActiveDate()).isEqualTo(LocalDate.of(2026, 9, 30));

        // Same instant for a Los Angeles learner (UTC-7): Sep 29 11:00 PDT -> Sep 29
        when(learners.findById(3L))
                .thenReturn(Optional.of(new Learner("l@la.us", "L", "hash", "America/Los_Angeles")));
        when(streaks.findById(3L)).thenReturn(Optional.empty());
        service.recordActivity(3L, Instant.parse("2026-09-29T18:00:00Z"));
        assertThat(captureSaved(3L).getLastActiveDate()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void repeatedSameDayActivitySavesAtMostOncePerCall() {
        Streak existing = streak(2, 2, LocalDate.of(2026, 9, 29));
        when(streaks.findById(LEARNER_ID)).thenReturn(Optional.of(existing));
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-29T23:00:00Z"));
        service.recordActivity(LEARNER_ID, Instant.parse("2026-09-30T00:30:00Z"));
        // 2026-09-30T00:30Z = Sep 29 20:30 EDT -> still the same local day
        Streak saved = captureSaved();
        assertThat(saved.getCurrentDays()).isEqualTo(2);
    }

    private static Streak streak(int currentDays, int longestDays, LocalDate lastActiveDate) {
        Streak streak = new Streak(LEARNER_ID);
        streak.setCurrentDays(currentDays);
        streak.setLongestDays(longestDays);
        streak.setLastActiveDate(lastActiveDate);
        return streak;
    }

    private Streak captureSaved() {
        return captureSaved(LEARNER_ID);
    }

    private Streak captureSaved(Long learnerId) {
        ArgumentCaptor<Streak> captor = ArgumentCaptor.forClass(Streak.class);
        verify(streaks, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
