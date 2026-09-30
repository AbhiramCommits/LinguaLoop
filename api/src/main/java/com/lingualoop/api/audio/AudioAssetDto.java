package com.lingualoop.api.audio;

public record AudioAssetDto(Long id, int durationMs, long bytes) {

    public static AudioAssetDto from(AudioAsset asset) {
        return new AudioAssetDto(asset.getId(), asset.getDurationMs(), asset.getBytes());
    }
}
