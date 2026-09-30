package com.lingualoop.api.experiment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByLearnerIdAndExperimentKey(Long learnerId, String experimentKey);

    @Query("""
            select a.learner.id from Assignment a
            where a.experimentKey = :experimentKey and a.variantKey = :variantKey
            """)
    List<Long> findLearnerIds(@Param("experimentKey") String experimentKey,
            @Param("variantKey") String variantKey);

    @Query("""
            select a.learner.id from Assignment a
            where a.experimentKey = :experimentKey and a.variantKey = :variantKey
              and a.learner.simulated = false
            """)
    List<Long> findRealLearnerIds(@Param("experimentKey") String experimentKey,
            @Param("variantKey") String variantKey);
}
