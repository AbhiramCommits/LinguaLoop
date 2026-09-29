package com.lingualoop.api.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByLanguageIdOrderByPositionAsc(Long languageId);
}
