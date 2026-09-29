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

    @Column(name = "asset_key", nullable = false, unique = true)
    private String assetKey;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    protected AudioAsset() {
    }

    public AudioAsset(String assetKey, String url, String mimeType, int durationMs) {
        this.assetKey = assetKey;
        this.url = url;
        this.mimeType = mimeType;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getDurationMs() {
        return durationMs;
    }
}
