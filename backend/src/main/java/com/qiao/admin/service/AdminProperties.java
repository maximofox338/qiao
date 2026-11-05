package com.qiao.admin.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code ADMIN_BOOTSTRAP_EMAIL}/{@code ADMIN_BOOTSTRAP_PASSWORD} and the admin login policy
 * (lock for 15 minutes after 5 failures).
 */
@ConfigurationProperties("app.admin")
public record AdminProperties(
        String bootstrapEmail,
        String bootstrapPassword,
        @DefaultValue("5") int maxFailedAttempts,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("10") int loginAttemptsPerIpPerMinute) {
}
