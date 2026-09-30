package com.lingualoop.api.experiment;

import java.time.Instant;

import com.lingualoop.api.learner.Learner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uq_assignment_learner_experiment", columnNames = { "learner_id", "experiment_key" })
})
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @Column(name = "experiment_key", nullable = false, length = 64)
    private String experimentKey;

    @Column(name = "variant_key", nullable = false, length = 64)
    private String variantKey;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected Assignment() {
    }

    public Assignment(Learner learner, String experimentKey, String variantKey) {
        this.learner = learner;
        this.experimentKey = experimentKey;
        this.variantKey = variantKey;
    }

    @PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Learner getLearner() {
        return learner;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
