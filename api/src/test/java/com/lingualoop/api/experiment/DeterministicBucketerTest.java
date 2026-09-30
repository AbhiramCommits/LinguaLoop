package com.lingualoop.api.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeterministicBucketerTest {

    @Test
    void hashIsUniformAndStable() {
        for (long learnerId = 1; learnerId <= 10_000; learnerId++) {
            double first = DeterministicBucketer.uniform01("lesson_ordering", learnerId);
            double second = DeterministicBucketer.uniform01("lesson_ordering", learnerId);
            assertThat(first).isEqualTo(second);
            assertThat(first).isBetween(0.0, 1.0);
        }
    }

    @Test
    void differentExperimentsHashIndependently() {
        long learnerId = 42L;
        double a = DeterministicBucketer.uniform01("lesson_ordering", learnerId);
        double b = DeterministicBucketer.uniform01("hint_timing", learnerId);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void fiftyFiftyDistributionWithinToleranceOver10kIds() {
        List<Double> weights = List.of(1.0, 1.0);
        int first = 0;
        int second = 0;
        for (long learnerId = 1; learnerId <= 10_000; learnerId++) {
            int index = DeterministicBucketer.selectVariant(
                    DeterministicBucketer.uniform01("fifty", learnerId), weights);
            if (index == 0) {
                first++;
            } else {
                second++;
            }
        }
        assertThat(first).isBetween(4800, 5200);
        assertThat(second).isBetween(4800, 5200);
    }

    @Test
    void ninetyTenDistributionWithinTolerance() {
        List<Double> weights = List.of(9.0, 1.0);
        int heavy = 0;
        int light = 0;
        for (long learnerId = 1; learnerId <= 10_000; learnerId++) {
            int index = DeterministicBucketer.selectVariant(
                    DeterministicBucketer.uniform01("ninety", learnerId), weights);
            if (index == 0) {
                heavy++;
            } else {
                light++;
            }
        }
        assertThat(heavy).isBetween(8800, 9200);
        assertThat(light).isBetween(800, 1200);
    }

    @Test
    void selectionRespectsBoundaries() {
        List<Double> weights = List.of(1.0, 1.0);
        assertThat(DeterministicBucketer.selectVariant(0.0, weights)).isZero();
        assertThat(DeterministicBucketer.selectVariant(0.4999, weights)).isZero();
        assertThat(DeterministicBucketer.selectVariant(0.5, weights)).isEqualTo(1);
        assertThat(DeterministicBucketer.selectVariant(0.9999, weights)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidWeights() {
        assertThatThrownBy(() -> DeterministicBucketer.selectVariant(0.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeterministicBucketer.selectVariant(0.5, List.of(1.0, -0.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
