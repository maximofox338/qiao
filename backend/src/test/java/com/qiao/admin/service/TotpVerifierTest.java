package com.qiao.admin.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpVerifierTest {

    /**
     * RFC 6238 appendix B: secret "12345678901234567890", T = 59 s → 94287082 (last 6 digits 287082).
     */
    @Test
    void matchesTheRfcTestVector() {
        TotpVerifier verifier = new TotpVerifier(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        assertThat(TotpVerifier.decodeBase32(secret)).isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(secret, "287082")).isTrue();
        assertThat(verifier.verify(secret, "287083")).isFalse();
        assertThat(verifier.verify(secret, "abc")).isFalse();
    }
}
