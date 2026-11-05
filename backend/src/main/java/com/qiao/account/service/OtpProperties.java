package com.qiao.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * OTP policy (spec §4): 10-minute codes, 5 wrong attempts then a 15-minute lock, 1 request/min per email,
 * 20 requests/hour per IP.
 */
@ConfigurationProperties("app.otp")
public record OtpProperties(
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("1") int requestsPerEmailPerMinute,
        @DefaultValue("20") int requestsPerIpPerHour) {
}
