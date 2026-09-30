package com.lingualoop.api.experiment.dto;

import java.util.List;

public record ExperimentResultsDto(String experimentKey, String status, String controlVariantKey,
        boolean includeSimulated, List<VariantResultsDto> variants) {

    public record VariantResultsDto(String key, boolean control, long n, boolean enoughData,
            Double d1ReturnRate, Double d1PValue, Double d7ReturnRate, Double d7PValue,
            Double meanSecondAttemptAccuracy, Double meanItemsPerSession) {
    }
}
