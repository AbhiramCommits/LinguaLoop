package com.lingualoop.api.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emails promoted to ADMIN at registration time. Set APP_ADMIN_EMAILS to
 * bootstrap the first admin on a fresh deployment (see docs/deploy.md).
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of() : emails;
    }
}
