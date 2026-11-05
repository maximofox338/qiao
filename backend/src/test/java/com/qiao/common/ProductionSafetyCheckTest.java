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
    private static final String PREVIOUS_PARTNER = "q4Kd8sZ1-previous-partner-key-9876543210cd";

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

    @Test
    void aRotationInProgressKeepsAValidPreviousPartnerKey() {
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setProperty("app.partner.api-key-previous", PREVIOUS_PARTNER);
        assertThatCode(() -> new ProductionSafetyCheck(env)).doesNotThrowAnyException();
        env.setProperty("app.partner.api-key-previous", "");
        assertThatCode(() -> new ProductionSafetyCheck(env)).doesNotThrowAnyException();
    }

    @Test
    void thePreviousPartnerKeyMustBeLongDifferentAndNextToACurrentKey() {
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setProperty("app.partner.api-key-previous", "short-previous-key");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("PARTNER_API_KEY_PREVIOUS")
                .hasMessageContaining("32");
        env.setProperty("app.partner.api-key-previous", PARTNER);
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("must differ");
        env.setProperty("app.partner.api-key-previous", "e2e-partner-key");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("PARTNER_API_KEY_PREVIOUS")
                .hasMessageContaining("public example");
        MockEnvironment noCurrent = prod(JWT, "", "");
        noCurrent.setProperty("app.partner.api-key-previous", PREVIOUS_PARTNER);
        assertThatThrownBy(() -> new ProductionSafetyCheck(noCurrent)).hasMessageContaining("PARTNER_API_KEY is empty");
    }

    @Test
    void contractPartyNamesAreRequiredWithRealPayments() {
        MockEnvironment unset = new MockEnvironment().withProperty("app.jwt.secret", JWT)
                .withProperty("app.partner.api-key", PARTNER);
        unset.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionSafetyCheck(unset)).hasMessageContaining("APP_CONTRACTS_HK_ENTITY_NAME")
                .hasMessageContaining("APP_CONTRACTS_PARTNER_ENTITY_NAME");

        for (String placeholder : new String[]{"", "   ", "[Hong Kong entity legal name]", "Qiao Bridge (HK) Limited [dev]",
                "[Razón social de la Ltd de Hong Kong]"}) {
            MockEnvironment env = prod(JWT, PARTNER, "");
            env.setProperty("app.contracts.hk-entity-name", placeholder);
            assertThatThrownBy(() -> new ProductionSafetyCheck(env)).as("hk name %s", placeholder)
                    .hasMessageContaining("APP_CONTRACTS_HK_ENTITY_NAME")
                    .hasMessageNotContaining("APP_CONTRACTS_PARTNER_ENTITY_NAME");
        }
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setProperty("app.contracts.partner-entity-name", "Qiao Partner Services Co., Ltd. [dev]");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("APP_CONTRACTS_PARTNER_ENTITY_NAME");
        // the prod profile defaults to Stripe
        env.setProperty("app.payment.provider", "stripe");
        assertThatThrownBy(() -> new ProductionSafetyCheck(env)).hasMessageContaining("PAYMENT_PROVIDER=fake");
    }

    @Test
    void aFakePaymentsTestRunStartsWithoutContractPartyNames() {
        MockEnvironment env = prod(JWT, PARTNER, "");
        env.setProperty("app.contracts.hk-entity-name", "");
        env.setProperty("app.contracts.partner-entity-name", "[PRC partner legal name]");
        env.setProperty("app.payment.provider", "fake");
        assertThatCode(() -> new ProductionSafetyCheck(env)).doesNotThrowAnyException();
    }

    @Test
    void realLegalNamesAreAccepted() {
        assertThat(ProductionSafetyCheck.isLegalName("Qiao Bridge (HK) Limited")).isTrue();
        assertThat(ProductionSafetyCheck.isLegalName("杭州桥梁人力资源服务有限公司")).isTrue();
        assertThat(ProductionSafetyCheck.isLegalName(null)).isFalse();
        assertThat(ProductionSafetyCheck.isLegalName("[dev]")).isFalse();
    }

    /**
     * A production environment with valid contract party names (each test changes what it checks).
     */
    private static MockEnvironment prod(String jwtSecret, String partnerKey, String bootstrapPassword) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.secret", jwtSecret)
                .withProperty("app.partner.api-key", partnerKey)
                .withProperty("app.admin.bootstrap-password", bootstrapPassword)
                .withProperty("app.contracts.hk-entity-name", "Qiao Bridge (HK) Limited")
                .withProperty("app.contracts.partner-entity-name", "杭州桥梁人力资源服务有限公司");
        env.setActiveProfiles("prod");
        return env;
    }
}
