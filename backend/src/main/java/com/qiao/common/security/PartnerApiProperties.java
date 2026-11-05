package com.qiao.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code PARTNER_API_KEY}; the partner API is disabled while it is blank. {@code PARTNER_API_KEY_PREVIOUS} (optional)
 * keeps the key being replaced valid during a rotation, so the partner can switch without downtime; calls made with it
 * are audited with a warning.
 */
@ConfigurationProperties("app.partner")
public record PartnerApiProperties(String apiKey, String apiKeyPrevious) {
}
