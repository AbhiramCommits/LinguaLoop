package com.lingualoop.api.experiment;

/**
 * Two-proportion z-test (pooled), implemented without external libraries.
 * Returns a two-tailed p-value, or null when the test is undefined
 * (zero-variance proportions, e.g. both groups at 0% or 100%, or empty
 * groups).
 */
public final class ZTest {

    private ZTest() {
    }

    public static Double twoProportionPValue(double p1, long n1, double p2, long n2) {
        if (n1 <= 0 || n2 <= 0) {
            return null;
        }
        double pooled = (p1 * n1 + p2 * n2) / (n1 + n2);
        if (pooled <= 0.0 || pooled >= 1.0) {
            return null;
        }
        double standardError = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        if (standardError == 0.0) {
            return null;
        }
        double z = (p1 - p2) / standardError;
        return 2.0 * (1.0 - normalCdf(Math.abs(z)));
    }

    static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /** Error function approximation, Abramowitz & Stegun 7.1.26 (|error| < 1.5e-7). */
    static double erf(double x) {
        double sign = Math.signum(x);
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }
}
