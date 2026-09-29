package com.lingualoop.api.learner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewStateRepository extends JpaRepository<ReviewState, Long> {

    Optional<ReviewState> findByLearnerIdAndExerciseId(Long learnerId, Long exerciseId);

    List<ReviewState> findByLearnerIdAndDueAtLessThanEqualOrderByDueAtAsc(Long learnerId, Instant now,
            org.springframework.data.domain.Pageable pageable);

    long countByLearnerIdAndDueAtLessThanEqual(Long learnerId, Instant now);

    long countByLearnerIdAndIntervalDaysGreaterThanEqual(Long learnerId, double intervalDays);
}
