package com.lingualoop.api.learner;

import java.time.Instant;

import com.lingualoop.api.learner.dto.QueueResponse;
import com.lingualoop.api.learner.dto.StatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learners/me")
@Tag(name = "learner", description = "Authenticated learner: review queue and stats")
@SecurityRequirement(name = "bearerAuth")
public class LearnerController {

    private final QueueService queueService;
    private final StatsService statsService;

    public LearnerController(QueueService queueService, StatsService statsService) {
        this.queueService = queueService;
        this.statsService = statsService;
    }

    @GetMapping("/queue")
    @Operation(summary = "Get the due review queue (spaced-repetition)")
    public QueueResponse queue(@AuthenticationPrincipal Long learnerId) {
        return queueService.getQueue(learnerId, Instant.now());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get learning statistics for the current learner")
    public StatsResponse stats(@AuthenticationPrincipal Long learnerId) {
        return statsService.stats(learnerId, Instant.now());
    }
}
