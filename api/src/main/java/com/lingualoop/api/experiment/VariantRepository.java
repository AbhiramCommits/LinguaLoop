package com.lingualoop.api.experiment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    List<Variant> findByExperimentKeyOrderByIdAsc(String experimentKey);

    Optional<Variant> findByExperimentKeyAndKey(String experimentKey, String key);
}
