package com.lingualoop.api.content;

import java.util.List;

import com.lingualoop.api.content.dto.LanguageDto;
import com.lingualoop.api.content.dto.LessonDto;
import com.lingualoop.api.content.dto.UnitDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "content", description = "Public language catalogue: languages, units, lessons, exercises")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/languages")
    @Operation(summary = "List all languages")
    public List<LanguageDto> languages() {
        return contentService.listLanguages();
    }

    @GetMapping("/languages/{id}/units")
    @Operation(summary = "List the units of a language")
    public List<UnitDto> unitsByLanguage(@PathVariable Long id) {
        return contentService.getUnitsByLanguage(id);
    }

    @GetMapping("/units/{id}")
    @Operation(summary = "Get a unit with its lessons")
    public UnitDto unit(@PathVariable Long id) {
        return contentService.getUnit(id);
    }

    @GetMapping("/lessons/{id}")
    @Operation(summary = "Get a lesson with its exercises")
    public LessonDto lesson(@PathVariable Long id) {
        return contentService.getLesson(id);
    }
}
