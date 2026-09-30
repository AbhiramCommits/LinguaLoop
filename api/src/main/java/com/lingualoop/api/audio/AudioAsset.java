package com.lingualoop.api.audio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audio_asset")
public class AudioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sha256;

    @Column(name = "opus_path", nullable = false, length = 500)
    private String opusPath;

    @Column(name = "mp3_path", nullable = false, length = 500)
    private String mp3Path;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(nullable = false)
    private long bytes;

    protected AudioAsset() {
    }

    public AudioAsset(String sha256, String opusPath, String mp3Path, int durationMs, long bytes) {
        this.sha256 = sha256;
        this.opusPath = opusPath;
        this.mp3Path = mp3Path;
        this.durationMs = durationMs;
        this.bytes = bytes;
    }

    public Long getId() {
        return id;
    }

    public String getSha256() {
        return sha256;
    }

    public String getOpusPath() {
        return opusPath;
    }

    public String getMp3Path() {
        return mp3Path;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public long getBytes() {
        return bytes;
    }
}
