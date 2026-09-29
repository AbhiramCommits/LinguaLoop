package com.lingualoop.api.learner;

import com.lingualoop.api.learner.dto.AttemptRequest;
import com.lingualoop.api.learner.dto.AttemptResultDto;
import com.lingualoop.api.learner.dto.CompleteSessionResponse;
import com.lingualoop.api.learner.dto.SessionDto;
import com.lingualoop.api.learner.dto.StartSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "session", description = "Study sessions and graded attempts")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final StudySessionService sessionService;

    public SessionController(StudySessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a study session for a lesson")
    public SessionDto start(@AuthenticationPrincipal Long learnerId,
            @Valid @RequestBody StartSessionRequest request) {
        return sessionService.start(learnerId, request.lessonId());
    }

    @PostMapping("/{id}/attempts")
    @Operation(summary = "Record a graded attempt against this session")
    public AttemptResultDto attempt(@AuthenticationPrincipal Long learnerId, @PathVariable Long id,
            @Valid @RequestBody AttemptRequest request) {
        return sessionService.submitAttempt(learnerId, id, request);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark the session completed")
    public CompleteSessionResponse complete(@AuthenticationPrincipal Long learnerId, @PathVariable Long id) {
        return sessionService.complete(learnerId, id);
    }
}
