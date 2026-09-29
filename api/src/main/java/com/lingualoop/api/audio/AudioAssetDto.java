package com.lingualoop.api.audio;

public record AudioAssetDto(Long id, String url, String mimeType, int durationMs) {

    public static AudioAssetDto from(AudioAsset asset) {
        return new AudioAssetDto(asset.getId(), asset.getUrl(), asset.getMimeType(), asset.getDurationMs());
    }
}
