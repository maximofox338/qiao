package com.qiao.common;

import com.qiao.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void slidingWindowAllowsAgainOnceOldHitsExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-05T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        RateLimiter limiter = new RateLimiter(clock);
        Duration minute = Duration.ofMinutes(1);

        assertThat(limiter.tryAcquire("otp:a", 1, minute)).isTrue();
        assertThat(limiter.tryAcquire("otp:a", 1, minute)).isFalse();
        assertThat(limiter.tryAcquire("otp:b", 1, minute)).isTrue();

        now.set(now.get().plusSeconds(61));
        assertThat(limiter.tryAcquire("otp:a", 1, minute)).isTrue();
    }
}
