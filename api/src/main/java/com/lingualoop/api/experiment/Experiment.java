package com.lingualoop.api.experiment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "experiment")
public class Experiment {

    @Id
    @Column(length = 64)
    private String key;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ExperimentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Experiment() {
    }

    public Experiment(String key, String description, ExperimentStatus status) {
        this.key = key;
        this.description = description;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
