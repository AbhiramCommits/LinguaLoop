package com.lingualoop.api.experiment;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

/**
 * Assigns an experiment variant to each new study session using a weighted
 * random choice. The variant key is stored on the session and is the only
 * experiment signal the API exposes; clients decide what the variant means.
 */
@Service
public class VariantService {

    private final ExperimentProperties properties;
    private final Random random = new SecureRandom();

    public VariantService(ExperimentProperties properties) {
        this.properties = properties;
    }

    public String assignVariant() {
        List<ExperimentProperties.Variant> variants = properties.variants();
        double total = variants.stream().mapToDouble(ExperimentProperties.Variant::weight).sum();
        double roll = random.nextDouble() * total;
        for (ExperimentProperties.Variant variant : variants) {
            roll -= variant.weight();
            if (roll <= 0) {
                return variant.key();
            }
        }
        return variants.get(variants.size() - 1).key();
    }
}
