package com.lingualoop.api.scheduler;

import java.time.Instant;

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

    @Transactional
    public ReviewState recordGrade(Learner learner, Exercise exercise, int grade, Instant now) {
        ReviewState state = reviewStates.findByLearnerIdAndExerciseId(learner.getId(), exercise.getId()).orElse(null);
        Sm2Scheduler.Outcome outcome = state == null
                ? Sm2Scheduler.firstReview(grade, now)
                : Sm2Scheduler.review(state.getEaseFactor(), state.getIntervalDays(), state.getRepetitions(),
                        state.getLapses(), grade, now);
        if (state == null) {
            state = new ReviewState(learner, exercise, outcome.dueAt());
        }
        state.setEaseFactor(outcome.easeFactor());
        state.setIntervalDays(outcome.intervalDays());
        state.setRepetitions(outcome.repetitions());
        state.setLapses(outcome.lapses());
        state.setDueAt(outcome.dueAt());
        state.setLastGrade((short) grade);
        return reviewStates.save(state);
    }
}
