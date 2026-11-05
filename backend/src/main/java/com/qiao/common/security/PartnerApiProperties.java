package com.qiao.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code PARTNER_API_KEY}; the partner API is disabled while it is blank.
 */
@ConfigurationProperties("app.partner")
public record PartnerApiProperties(String apiKey) {
}
