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

    @Test
    void reportsWhichTimeStepACodeBelongsTo() {
        long now = 1_800_000_015L; // step 60 000 000, 15 s into it
        TotpVerifier verifier = new TotpVerifier(Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC));
        String secret = verifier.generateSecret();
        long step = now / 30;

        assertThat(verifier.currentStep()).isEqualTo(step);
        assertThat(verifier.matchStep(secret, TotpVerifier.codeAt(secret, step))).hasValue(step);
        assertThat(verifier.matchStep(secret, TotpVerifier.codeAt(secret, step - 1))).hasValue(step - 1);
        assertThat(verifier.matchStep(secret, TotpVerifier.codeAt(secret, step + 1))).hasValue(step + 1);
        // outside the ±1 step drift window
        String old = TotpVerifier.codeAt(secret, step - 2);
        if (!old.equals(TotpVerifier.codeAt(secret, step - 1)) && !old.equals(TotpVerifier.codeAt(secret, step))
                && !old.equals(TotpVerifier.codeAt(secret, step + 1))) {
            assertThat(verifier.matchStep(secret, old)).isEmpty();
        }
        assertThat(verifier.matchStep(null, "123456")).isEmpty();
    }

    @Test
    void generatesBase32SecretsAndOtpauthUris() {
        TotpVerifier verifier = new TotpVerifier(Clock.systemUTC());
        String secret = verifier.generateSecret();

        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(TotpVerifier.decodeBase32(secret)).hasSize(20);
        assertThat(TotpVerifier.encodeBase32(TotpVerifier.decodeBase32(secret))).isEqualTo(secret);
        assertThat(TotpVerifier.encodeBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(verifier.generateSecret()).isNotEqualTo(secret);
        assertThat(TotpVerifier.otpauthUri("Qiao", "ops+1@qiao.example", secret))
                .isEqualTo("otpauth://totp/Qiao:ops%2B1%40qiao.example?secret=" + secret
                        + "&issuer=Qiao&algorithm=SHA1&digits=6&period=30");
    }
}
