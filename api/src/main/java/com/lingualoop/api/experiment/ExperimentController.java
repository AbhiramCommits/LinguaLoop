package com.lingualoop.api.experiment;

import java.util.List;

import com.lingualoop.api.experiment.dto.ExperimentDto;
import com.lingualoop.api.experiment.dto.ExperimentResultsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
@Tag(name = "experiment", description = "Experiment definitions and retention results")
@SecurityRequirement(name = "bearerAuth")
public class ExperimentController {

    private final ExperimentRepository experiments;
    private final VariantRepository variants;
    private final ExperimentResultsService resultsService;

    public ExperimentController(ExperimentRepository experiments, VariantRepository variants,
            ExperimentResultsService resultsService) {
        this.experiments = experiments;
        this.variants = variants;
        this.resultsService = resultsService;
    }

    @GetMapping
    @Operation(summary = "List experiments and their variants")
    public List<ExperimentDto> list() {
        return experiments.findAllByOrderByKeyAsc().stream()
                .map(experiment -> new ExperimentDto(
                        experiment.getKey(),
                        experiment.getDescription(),
                        experiment.getStatus(),
                        variants.findByExperimentKeyOrderByIdAsc(experiment.getKey()).stream()
                                .map(variant -> new ExperimentDto.VariantDto(
                                        variant.getKey(), variant.getWeight(), variant.isControl()))
                                .toList()))
                .toList();
    }

    @GetMapping("/{key}/results")
    @Operation(summary = "Retention results per variant, with z-test p-values against control")
    public ExperimentResultsDto results(
            @PathVariable String key,
            @RequestParam(name = "includeSimulated", defaultValue = "false") boolean includeSimulated) {
        return resultsService.results(key, includeSimulated);
    }
}
