package com.lingualoop.api.learner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreakService {

    private final StreakRepository streaks;
    private final LearnerRepository learners;

    public StreakService(StreakRepository streaks, LearnerRepository learners) {
        this.streaks = streaks;
        this.learners = learners;
    }

    @Transactional
    public void recordActivity(Long learnerId, Instant now) {
        Learner learner = learners.findById(learnerId).orElse(null);
        ZoneId zone = learner == null ? ZoneId.of("UTC") : ZoneId.of(learner.getTimezone());
        LocalDate today = now.atZone(zone).toLocalDate();

        Streak streak = streaks.findById(learnerId).orElseGet(() -> new Streak(learnerId));
        LocalDate last = streak.getLastActiveDate();
        if (last == null || last.equals(today.minusDays(1))) {
            streak.setCurrentDays(streak.getCurrentDays() + 1);
        } else if (!last.equals(today)) {
            streak.setCurrentDays(1);
        }
        streak.setLongestDays(Math.max(streak.getLongestDays(), streak.getCurrentDays()));
        streak.setLastActiveDate(today);
        streaks.save(streak);
    }

    @Transactional(readOnly = true)
    public Streak get(Long learnerId) {
        return streaks.findById(learnerId).orElseGet(() -> new Streak(learnerId));
    }
}
