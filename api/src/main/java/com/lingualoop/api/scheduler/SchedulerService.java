package com.lingualoop.api.scheduler;

import java.time.Instant;
import java.time.ZoneId;

import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.ReviewState;
import com.lingualoop.api.learner.ReviewStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerService {

    private final ReviewStateRepository reviewStates;

    public SchedulerService(ReviewStateRepository reviewStates) {
        this.reviewStates = reviewStates;
    }

    /**
     * Runs the SM-2 rules for the given grade and upserts the learner's
     * review_state row for the exercise. A first-time exercise starts from
     * the scheduler defaults (ease 2.5, interval 0, repetitions 0, lapses 0).
     */
    @Transactional
    public ReviewState recordGrade(Learner learner, Exercise exercise, int grade, ZoneId timezone, Instant now) {
        ReviewState existing = reviewStates.findByLearnerIdAndExerciseId(learner.getId(), exercise.getId())
                .orElse(null);
        ReviewState current = existing != null ? existing : new ReviewState(learner, exercise, now);
        ReviewState updated = Sm2Scheduler.schedule(current, grade, timezone, now);
        if (existing == null) {
            return reviewStates.save(updated);
        }
        apply(existing, updated);
        return reviewStates.save(existing);
    }

    private static void apply(ReviewState target, ReviewState source) {
        target.setEaseFactor(source.getEaseFactor());
        target.setIntervalDays(source.getIntervalDays());
        target.setRepetitions(source.getRepetitions());
        target.setLapses(source.getLapses());
        target.setDueAt(source.getDueAt());
        target.setLastGrade(source.getLastGrade());
    }
}
