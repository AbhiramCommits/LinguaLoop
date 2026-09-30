package com.lingualoop.api.experiment;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "variant", uniqueConstraints = {
        @UniqueConstraint(name = "uq_variant_experiment_key", columnNames = { "experiment_key", "key" })
})
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experiment_key", nullable = false, length = 64)
    private String experimentKey;

    @Column(nullable = false, length = 64)
    private String key;

    @Column(nullable = false)
    private double weight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean control;

    protected Variant() {
    }

    public Variant(String experimentKey, String key, double weight, Map<String, Object> config, boolean control) {
        this.experimentKey = experimentKey;
        this.key = key;
        this.weight = weight;
        this.config = config == null ? new LinkedHashMap<>() : config;
        this.control = control;
    }

    public Long getId() {
        return id;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public String getKey() {
        return key;
    }

    public double getWeight() {
        return weight;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public boolean isControl() {
        return control;
    }
}
