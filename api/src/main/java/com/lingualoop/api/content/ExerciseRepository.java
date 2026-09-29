package com.lingualoop.api.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByLessonIdOrderByIdAsc(Long lessonId);

    long countByLessonId(Long lessonId);
}
