package com.lingualoop.api.learner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    long countBySessionId(Long sessionId);

    long countBySessionLearnerId(Long learnerId);

    @Query("select count(distinct a.exercise.id) from Attempt a where a.session.learner.id = :learnerId")
    long countDistinctExercisesByLearner(@Param("learnerId") Long learnerId);

    @Query("select avg(cast(a.grade as double)) from Attempt a where a.session.learner.id = :learnerId")
    Double averageGradeByLearner(@Param("learnerId") Long learnerId);

    @Query("select avg(cast(a.grade as double)) from Attempt a where a.session.id = :sessionId")
    Double averageGradeBySession(@Param("sessionId") Long sessionId);
}
