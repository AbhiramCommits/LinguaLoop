package com.lingualoop.api.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.lingualoop.api.common.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audio")
@Tag(name = "audio", description = "Audio assets for LISTEN exercises: metadata and streaming")
public class AudioController {

    private static final MediaType OPUS_TYPE = MediaType.parseMediaType("audio/ogg");
    private static final MediaType MP3_TYPE = MediaType.parseMediaType("audio/mpeg");
    private static final CacheControl IMMUTABLE_CACHE = CacheControl.maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();

    private final AudioAssetRepository audioAssets;
    private final AudioProperties properties;

    public AudioController(AudioAssetRepository audioAssets, AudioProperties properties) {
        this.audioAssets = audioAssets;
        this.properties = properties;
    }

    @GetMapping("/assets/{id}")
    @Operation(summary = "Get audio asset metadata")
    public AudioAssetDto metadata(@PathVariable Long id) {
        return AudioAssetDto.from(load(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Stream an audio asset (Opus preferred, MP3 fallback); supports ETag, Range and immutable caching")
    public ResponseEntity<?> stream(@PathVariable Long id, @RequestHeader HttpHeaders headers) throws IOException {
        AudioAsset asset = load(id);
        boolean opus = prefersOpus(headers.getAccept());
        String relativePath = opus ? asset.getOpusPath() : asset.getMp3Path();
        MediaType mediaType = opus ? OPUS_TYPE : MP3_TYPE;
        String etag = "\"" + asset.getSha256() + (opus ? "-opus" : "-mp3") + "\"";
        Path file = resolve(relativePath);
        long size = Files.size(file);

        if (headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(IMMUTABLE_CACHE)
                    .eTag(etag)
                    .build();
        }

        List<HttpRange> ranges = headers.getRange();
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .cacheControl(IMMUTABLE_CACHE)
                    .eTag(etag)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(size)
                    .body(new FileSystemResource(file));
        }

        long start;
        long end;
        try {
            ResourceRegion region = ranges.get(0).toResourceRegion(new FileSystemResource(file));
            start = region.getPosition();
            end = start + region.getCount() - 1;
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                    .cacheControl(IMMUTABLE_CACHE)
                    .build();
        }

        int length = (int) (end - start + 1);
        byte[] body;
        try (var in = Files.newInputStream(file)) {
            in.skipNBytes(start);
            body = in.readNBytes(length);
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .cacheControl(IMMUTABLE_CACHE)
                .eTag(etag)
                .contentType(mediaType)
                .contentLength(length)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size)
                .body(body);
    }

    private AudioAsset load(Long id) {
        return audioAssets.findById(id)
                .orElseThrow(() -> new NotFoundException("Audio asset " + id + " not found"));
    }

    /**
     * Opus when the client's Accept header allows audio/ogg (or any audio,
     * or omits Accept entirely), MP3 otherwise.
     */
    static boolean prefersOpus(List<MediaType> accept) {
        for (MediaType type : accept) {
            if (type.isCompatibleWith(OPUS_TYPE) || type.isCompatibleWith(MediaType.parseMediaType("audio/opus"))) {
                return true;
            }
            if (type.isCompatibleWith(MP3_TYPE) || type.isCompatibleWith(MediaType.parseMediaType("audio/mp3"))) {
                return false;
            }
            if (type.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)
                    || type.isCompatibleWith(MediaType.parseMediaType("audio/*"))
                    || type.isCompatibleWith(MediaType.ALL)) {
                return true;
            }
        }
        return true;
    }

    private Path resolve(String relativePath) {
        Path base = Path.of(properties.directory()).toAbsolutePath().normalize();
        Path file = base.resolve(relativePath).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new NotFoundException("Audio file for this asset is not available");
        }
        return file;
    }
}
