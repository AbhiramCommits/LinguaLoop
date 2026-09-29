package com.lingualoop.api.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.audio")
public record AudioProperties(String directory) {
}
