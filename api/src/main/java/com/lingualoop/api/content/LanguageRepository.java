package com.lingualoop.api.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageRepository extends JpaRepository<Language, Long> {

    List<Language> findAllByOrderByIdAsc();

    Optional<Language> findByCode(String code);
}
