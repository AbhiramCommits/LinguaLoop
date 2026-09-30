package com.lingualoop.api.learner;

import java.util.List;

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

    @Query("""
            select a.session.learner.id, a.exercise.id, a.grade, a.createdAt
            from Attempt a
            where a.session.learner.id in :learnerIds
            order by a.createdAt asc
            """)
    List<Object[]> findAttemptHistoryByLearnerIds(@Param("learnerIds") List<Long> learnerIds);

    @Query("""
            select a.session.id, count(a)
            from Attempt a
            where a.session.learner.id in :learnerIds
            group by a.session.id
            """)
    List<Object[]> countAttemptsPerSessionByLearnerIds(@Param("learnerIds") List<Long> learnerIds);
}
