package com.lingualoop.api.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ZTestTest {

    @Test
    void knownValueModerateDifference() {
        // p1 = 0.5 (n=100) vs p2 = 0.3 (n=100):
        // pooled = 0.4, se = sqrt(0.4*0.6*(0.01+0.01)) = 0.06928, z = 2.8868 -> p = 0.00387
        Double p = ZTest.twoProportionPValue(0.5, 100, 0.3, 100);
        assertThat(p).isNotNull();
        assertThat(p).isCloseTo(0.00387, Offset.offset(0.0001));
    }

    @Test
    void identicalProportionsGivePValueOne() {
        Double p = ZTest.twoProportionPValue(0.2, 1000, 0.2, 1000);
        assertThat(p).isEqualTo(1.0);
    }

    @Test
    void smallDifferenceIsNotSignificant() {
        Double p = ZTest.twoProportionPValue(0.31, 100, 0.3, 100);
        assertThat(p).isCloseTo(0.88, Offset.offset(0.01));
    }

    @Test
    void zeroVarianceIsUndefined() {
        assertThat(ZTest.twoProportionPValue(0.0, 50, 0.0, 50)).isNull();
        assertThat(ZTest.twoProportionPValue(1.0, 50, 1.0, 50)).isNull();
    }

    @Test
    void emptyGroupsAreUndefined() {
        assertThat(ZTest.twoProportionPValue(0.5, 0, 0.5, 100)).isNull();
        assertThat(ZTest.twoProportionPValue(0.5, 100, 0.5, 0)).isNull();
    }

    @Test
    void normalCdfSanity() {
        assertThat(ZTest.normalCdf(0.0)).isCloseTo(0.5, Offset.offset(1e-9));
        assertThat(ZTest.normalCdf(1.96)).isCloseTo(0.975, Offset.offset(0.0001));
        assertThat(ZTest.normalCdf(-1.96)).isCloseTo(0.025, Offset.offset(0.0001));
    }
}
