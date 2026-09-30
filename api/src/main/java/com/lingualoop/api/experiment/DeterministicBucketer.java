package com.lingualoop.api.experiment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Deterministic, stable bucketing: SHA-256 of "{experimentKey}:{learnerId}"
 * mapped to a uniform value in [0,1), then into weighted variant ranges.
 * The same learner always hashes to the same variant for a given experiment,
 * regardless of process restarts. Persisted assignments take precedence over
 * this hash (see {@link ExperimentClient}); the hash only decides first
 * exposure, so later weight edits never reshuffle already-assigned learners.
 */
public final class DeterministicBucketer {

    private DeterministicBucketer() {
    }

    public static double uniform01(String experimentKey, long learnerId) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest((experimentKey + ":" + learnerId).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
        long value = ByteBuffer.wrap(digest, 0, 8).getLong();
        return (value & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
    }

    /** Selects the variant index for a uniform value in [0,1) given positive weights. */
    public static int selectVariant(double uniform, List<Double> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        double total = 0;
        for (double weight : weights) {
            if (weight <= 0) {
                throw new IllegalArgumentException("weights must be positive");
            }
            total += weight;
        }
        double roll = uniform * total;
        for (int i = 0; i < weights.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }
}
