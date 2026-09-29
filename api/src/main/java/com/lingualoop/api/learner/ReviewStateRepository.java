package com.lingualoop.api.learner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewStateRepository extends JpaRepository<ReviewState, Long> {

    Optional<ReviewState> findByLearnerIdAndExerciseId(Long learnerId, Long exerciseId);

    @Query("""
            select rs from ReviewState rs
              join fetch rs.exercise e
              join fetch e.lesson l
              join fetch l.unit u
              left join fetch e.audioAsset
            where rs.learner.id = :learnerId and rs.dueAt <= :now
            order by rs.dueAt asc
            """)
    List<ReviewState> findDueWithDetails(@Param("learnerId") Long learnerId, @Param("now") Instant now,
            Pageable pageable);

    long countByLearnerIdAndDueAtLessThanEqual(Long learnerId, Instant now);

    long countByLearnerIdAndIntervalDaysGreaterThanEqual(Long learnerId, double intervalDays);
}
