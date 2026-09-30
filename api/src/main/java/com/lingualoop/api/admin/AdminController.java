package com.lingualoop.api.admin.imports;

import java.util.List;

import com.lingualoop.api.admin.AdminContentService;
import com.lingualoop.api.common.error.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lingualoop.api.admin.dto.ExerciseRequest;
import com.lingualoop.api.admin.dto.LanguageRequest;
import com.lingualoop.api.admin.dto.LessonRequest;
import com.lingualoop.api.admin.dto.UnitRequest;
import com.lingualoop.api.content.dto.ExerciseDto;
import com.lingualoop.api.content.dto.LanguageDto;
import com.lingualoop.api.content.dto.LessonDto;
import com.lingualoop.api.content.dto.UnitDto;

import jakarta.validation.Valid;

/**
 * Admin content management. Every route requires an ADMIN role on the JWT;
 * promote a learner with
 *   UPDATE learner SET role='ADMIN' WHERE email='...'
 * (see docs/authoring.md and docs/deploy.md).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin", description = "Admin-only content authoring and import")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminContentService content;
    private final ImportService imports;

    public AdminController(AdminContentService content, ImportService imports) {
        this.content = content;
        this.imports = imports;
    }

    @PostMapping("/languages")
    @Operation(summary = "Create a language")
    public LanguageDto createLanguage(@Valid @RequestBody LanguageRequest request) {
        return content.createLanguage(request);
    }

    @PutMapping("/languages/{id}")
    @Operation(summary = "Update a language")
    public LanguageDto updateLanguage(@PathVariable Long id, @Valid @RequestBody LanguageRequest request) {
        return content.updateLanguage(id, request);
    }

    @DeleteMapping("/languages/{id}")
    @Operation(summary = "Delete a language (cascades to units/lessons/exercises)")
    public void deleteLanguage(@PathVariable Long id) {
        content.deleteLanguage(id);
    }

    @PostMapping("/units")
    @Operation(summary = "Create a unit")
    public UnitDto createUnit(@Valid @RequestBody UnitRequest request) {
        return content.createUnit(request);
    }

    @PutMapping("/units/{id}")
    @Operation(summary = "Update a unit")
    public UnitDto updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return content.updateUnit(id, request);
    }

    @DeleteMapping("/units/{id}")
    @Operation(summary = "Delete a unit (cascades to lessons/exercises)")
    public void deleteUnit(@PathVariable Long id) {
        content.deleteUnit(id);
    }

    @PostMapping("/lessons")
    @Operation(summary = "Create a lesson")
    public LessonDto createLesson(@Valid @RequestBody LessonRequest request) {
        return content.createLesson(request);
    }

    @PutMapping("/lessons/{id}")
    @Operation(summary = "Update a lesson")
    public LessonDto updateLesson(@PathVariable Long id, @Valid @RequestBody LessonRequest request) {
        return content.updateLesson(id, request);
    }

    @DeleteMapping("/lessons/{id}")
    @Operation(summary = "Delete a lesson (cascades to exercises)")
    public void deleteLesson(@PathVariable Long id) {
        content.deleteLesson(id);
    }

    @PostMapping("/exercises")
    @Operation(summary = "Create an exercise")
    public ExerciseDto createExercise(@Valid @RequestBody ExerciseRequest request) {
        return content.createExercise(request);
    }

    @PutMapping("/exercises/{id}")
    @Operation(summary = "Update an exercise")
    public ExerciseDto updateExercise(@PathVariable Long id, @Valid @RequestBody ExerciseRequest request) {
        return content.updateExercise(id, request);
    }

    @DeleteMapping("/exercises/{id}")
    @Operation(summary = "Delete an exercise")
    public void deleteExercise(@PathVariable Long id) {
        content.deleteExercise(id);
    }

    @PostMapping(value = "/import", consumes = "text/plain")
    @Operation(summary = "Import a unit bundle (YAML or CSV); dry-run reports the plan without persisting")
    public ImportResponseDto importBundle(
            @RequestBody String contentBody,
            @RequestParam(defaultValue = "yaml") String format,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        if (contentBody == null || contentBody.isBlank()) {
            throw new BadRequestException("The import body must not be empty");
        }
        UnitBundle bundle = ImportParser.parse(contentBody, format);
        if (dryRun) {
            ImportService.Plan plan = imports.plan(bundle);
            return ImportResponseDto.of(plan, bundle, true);
        }
        ImportService.Plan plan = imports.apply(bundle);
        return ImportResponseDto.of(plan, bundle, false);
    }
}
