package com.lingualoop.api.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.queue")
public record QueueProperties(Duration cacheTtl, int limit) {
}
