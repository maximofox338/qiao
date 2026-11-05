package com.qiao.common;

import com.qiao.account.service.DevOtpRegistry;
import com.qiao.common.config.ProductionSafetyCheck;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyCheckTest {

    private static final String JWT = "k3J9f0aQm2-random-production-secret-7Zx1";
    private static final String PARTNER = "p8Vb2nW5-random-partner-key-0123456789ab";

    @Test
    void prodWithRandomSecretsStarts() {
        assertThatCode(() -> new ProductionSafetyCheck(prod(JWT, PARTNER, "Bootstrap-pass-9271")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ProductionSafetyCheck(prod(JWT, "", ""))).doesNotThrowAnyException();
    }

    @Test
    void prodCannotRunWithTheDevProfile() {
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setActiveProfiles("prod", "dev");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("dev profile");
    }

    @Test
    void publicExampleSecretsAreRefused() {
        assertThatThrownBy(() -> new ProductionSafetyCheck(
                prod("cambiar-por-un-secreto-largo-y-aleatorio-de-al-menos-32-caracteres", PARTNER, "")))
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new ProductionSafetyCheck(prod(JWT, "dev-partner-key", "")))
                .hasMessageContaining("PARTNER_API_KEY");
        assertThatThrownBy(() -> new ProductionSafetyCheck(prod(JWT, PARTNER, "cambiar-por-una-clave-de-12+caracteres")))
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void shortPartnerKeysAreRefusedEvenWithoutAProfile() {
        MockEnvironment env = prod(JWT, "short-partner-key", "");
        env.setActiveProfiles();
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("PARTNER_API_KEY");
    }

    @Test
    void stripeApiBaseCannotPointElsewhereInProduction() {
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setProperty("app.payment.stripe-api-base", "http://localhost:12111");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("STRIPE_API_BASE");
        env.setProperty("app.payment.stripe-api-base", "https://api.stripe.com");
        assertThatCode(() -> new ProductionSafetyCheck(env)).doesNotThrowAnyException();
    }

    @Test
    void localDevelopmentKeepsItsDefaults() {
        MockEnvironment env = prod("dev-only-jwt-secret-change-me-0123456789abcdef", "dev-partner-key",
                "QiaoAdmin-dev-2026");
        env.setActiveProfiles("dev");
        assertThatCode(() -> new ProductionSafetyCheck(env)).doesNotThrowAnyException();
    }

    @Test
    void devOtpEndpointIsNeverEnabledWithProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "dev");
        assertThat(env.acceptsProfiles(Profiles.of(DevOtpRegistry.PROFILES))).isFalse();
        env.setActiveProfiles("prod", "test");
        assertThat(env.acceptsProfiles(Profiles.of(DevOtpRegistry.PROFILES))).isFalse();
        env.setActiveProfiles("dev");
        assertThat(env.acceptsProfiles(Profiles.of(DevOtpRegistry.PROFILES))).isTrue();
    }

    private static MockEnvironment prod(String jwtSecret, String partnerKey, String bootstrapPassword) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.secret", jwtSecret)
                .withProperty("app.partner.api-key", partnerKey)
                .withProperty("app.admin.bootstrap-password", bootstrapPassword);
        env.setActiveProfiles("prod");
        return env;
    }
}
