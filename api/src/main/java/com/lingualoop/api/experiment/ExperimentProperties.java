package com.lingualoop.api.experiment;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.experiment")
public record ExperimentProperties(List<Variant> variants) {

    public record Variant(String key, double weight) {
    }

    public ExperimentProperties {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalStateException("app.experiment.variants must define at least one variant");
        }
        if (variants.stream().anyMatch(v -> v.weight() <= 0)) {
            throw new IllegalStateException("app.experiment.variants weights must be > 0");
        }
    }
}
