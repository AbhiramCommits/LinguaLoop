package com.lingualoop.api.learner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.lingualoop.api.learner.dto.StatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    private static final double MASTERED_INTERVAL_DAYS = 21.0;

    private final AttemptRepository attempts;
    private final ReviewStateRepository reviewStates;
    private final StudySessionRepository sessions;
    private final StreakService streakService;

    public StatsService(AttemptRepository attempts, ReviewStateRepository reviewStates,
            StudySessionRepository sessions, StreakService streakService) {
        this.attempts = attempts;
        this.reviewStates = reviewStates;
        this.sessions = sessions;
        this.streakService = streakService;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(Long learnerId, Instant now) {
        Streak streak = streakService.get(learnerId);
        Double averageGrade = round2(attempts.averageGradeByLearner(learnerId));
        return new StatsResponse(
                attempts.countBySessionLearnerId(learnerId),
                attempts.countDistinctExercisesByLearner(learnerId),
                reviewStates.countByLearnerIdAndIntervalDaysGreaterThanEqual(learnerId, MASTERED_INTERVAL_DAYS),
                averageGrade,
                reviewStates.countByLearnerIdAndDueAtLessThanEqual(learnerId, now),
                sessions.countByLearnerIdAndEndedAtIsNotNull(learnerId),
                new StatsResponse.StreakDto(streak.getCurrentDays(), streak.getLongestDays(),
                        streak.getLastActiveDate()));
    }

    private static Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
