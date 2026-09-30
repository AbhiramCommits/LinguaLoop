package com.lingualoop.api.learner;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByIdAndLearnerId(Long id, Long learnerId);

    Optional<StudySession> findTopByLearnerIdOrderByIdDesc(Long learnerId);

    long countByLearnerIdAndEndedAtIsNotNull(Long learnerId);

    List<StudySession> findByLearnerIdAndEndedAtIsNull(Long learnerId);

    @Query("select s.learner.id, s.startedAt from StudySession s where s.learner.id in :learnerIds")
    List<Object[]> findStartedAtByLearnerIds(@Param("learnerIds") List<Long> learnerIds);
}
