package com.lingualoop.api.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class VariantServiceTest {

    @Test
    void singleVariantIsAlwaysChosen() {
        var properties = new ExperimentProperties(List.of(new ExperimentProperties.Variant("control", 1.0)));
        var service = new VariantService(properties);
        for (int i = 0; i < 100; i++) {
            assertThat(service.assignVariant()).isEqualTo("control");
        }
    }

    @Test
    void weightedChoiceRespectsWeightsRoughly() {
        var properties = new ExperimentProperties(List.of(
                new ExperimentProperties.Variant("control", 9.0),
                new ExperimentProperties.Variant("experimental", 1.0)));
        var service = new VariantService(properties);

        int experimental = 0;
        for (int i = 0; i < 10_000; i++) {
            if (service.assignVariant().equals("experimental")) {
                experimental++;
            }
        }
        assertThat(experimental).isBetween(500, 1500);
    }
}
