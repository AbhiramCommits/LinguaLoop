package com.lingualoop.api.learner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.content.Unit;
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
    private final ActiveUnitResolver activeUnitResolver;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;

    public StatsService(AttemptRepository attempts, ReviewStateRepository reviewStates,
            StudySessionRepository sessions, StreakService streakService, ActiveUnitResolver activeUnitResolver,
            LessonRepository lessons, ExerciseRepository exercises) {
        this.attempts = attempts;
        this.reviewStates = reviewStates;
        this.sessions = sessions;
        this.streakService = streakService;
        this.activeUnitResolver = activeUnitResolver;
        this.lessons = lessons;
        this.exercises = exercises;
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
                        streak.getLastActiveDate()),
                buildUnitMastery(learnerId));
    }

    /**
     * Mastery per lesson = fraction of its exercises with review_state
     * repetitions >= 3 and easeFactor >= 2.0. Unit mastery is the same
     * fraction across the unit's exercises.
     */
    private StatsResponse.UnitMasteryDto buildUnitMastery(Long learnerId) {
        return activeUnitResolver.resolve(learnerId)
                .map(unit -> toUnitMastery(learnerId, unit))
                .orElse(null);
    }

    private StatsResponse.UnitMasteryDto toUnitMastery(Long learnerId, Unit unit) {
        long masteredTotal = 0;
        long exerciseTotal = 0;
        List<StatsResponse.LessonMasteryDto> lessonMasteries = new ArrayList<>();
        for (Lesson lesson : lessons.findByUnitIdOrderByPositionAsc(unit.getId())) {
            long total = exercises.countByLessonId(lesson.getId());
            long mastered = reviewStates.countMasteredInLesson(learnerId, lesson.getId());
            masteredTotal += mastered;
            exerciseTotal += total;
            lessonMasteries.add(new StatsResponse.LessonMasteryDto(
                    lesson.getId(), lesson.getTitle(), fraction(mastered, total), mastered, total));
        }
        return new StatsResponse.UnitMasteryDto(
                unit.getId(), unit.getTitle(), fraction(masteredTotal, exerciseTotal), lessonMasteries);
    }

    private static double fraction(long mastered, long total) {
        if (total == 0) {
            return 0.0;
        }
        return round2(BigDecimal.valueOf(mastered).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue());
    }

    private static Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
