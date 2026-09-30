package com.lingualoop.api.experiment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<Experiment, String> {

    List<Experiment> findAllByOrderByKeyAsc();
}
