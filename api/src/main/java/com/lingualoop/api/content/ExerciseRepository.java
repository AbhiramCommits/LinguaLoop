package com.lingualoop.api.content;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByLessonIdOrderByIdAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    @Query("""
            select e from Exercise e
              join fetch e.lesson l
              join fetch l.unit u
              left join fetch e.audioAsset
            where l.unit.id = :unitId
              and not exists (
                select 1 from ReviewState rs
                where rs.learner.id = :learnerId and rs.exercise.id = e.id)
            order by l.position asc, e.id asc
            """)
    List<Exercise> findUnseenInUnit(@Param("unitId") Long unitId, @Param("learnerId") Long learnerId,
            Pageable pageable);
}
