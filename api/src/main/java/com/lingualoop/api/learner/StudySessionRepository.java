package com.lingualoop.api.learner;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByIdAndLearnerId(Long id, Long learnerId);

    Optional<StudySession> findTopByLearnerIdOrderByIdDesc(Long learnerId);

    long countByLearnerIdAndEndedAtIsNotNull(Long learnerId);

    List<StudySession> findByLearnerIdAndEndedAtIsNull(Long learnerId);
}
