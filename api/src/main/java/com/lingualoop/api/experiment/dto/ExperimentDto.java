package com.lingualoop.api.experiment.dto;

import java.util.List;

import com.lingualoop.api.experiment.ExperimentStatus;

public record ExperimentDto(String key, String description, ExperimentStatus status, List<VariantDto> variants) {

    public record VariantDto(String key, double weight, boolean control) {
    }
}
