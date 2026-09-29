package com.lingualoop.api.audio;

import com.lingualoop.api.common.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audio")
@Tag(name = "audio", description = "Audio asset metadata for LISTEN exercises")
public class AudioController {

    private final AudioAssetRepository audioAssets;

    public AudioController(AudioAssetRepository audioAssets) {
        this.audioAssets = audioAssets;
    }

    @GetMapping("/assets/{id}")
    @Operation(summary = "Get audio asset metadata")
    public AudioAssetDto get(@PathVariable Long id) {
        AudioAsset asset = audioAssets.findById(id)
                .orElseThrow(() -> new NotFoundException("Audio asset " + id + " not found"));
        return AudioAssetDto.from(asset);
    }
}
