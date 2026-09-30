package com.lingualoop.api.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByUnitIdOrderByPositionAsc(Long unitId);

    boolean existsByUnitIdAndPosition(Long unitId, int position);
}
