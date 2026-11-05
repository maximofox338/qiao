package com.qiao.common.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start a production deployment ({@code prod} profile, or no {@code dev}/{@code test} profile) with
 * secrets anyone can read in this repository: the {@code dev} profile (whose defaults include a JWT secret, a partner
 * key and an admin password), the placeholders of {@code deploy/.env.example} and the values of the e2e script.
 * Those values pass the length checks, so without this guard a forgotten placeholder would let anyone forge admin
 * tokens, call the partner API or sign in as the bootstrap admin.
 */
@Component
public class ProductionSafetyCheck {

    static final int MIN_PARTNER_KEY_LENGTH = 32;

    private static final Set<String> PUBLIC_VALUES = Set.of(
            // application-dev.yml defaults
            "dev-only-jwt-secret-change-me-0123456789abcdef", "dev-partner-key", "QiaoAdmin-dev-2026",
            // deploy/.env.example placeholders
            "cambiar-por-un-secreto-largo-y-aleatorio-de-al-menos-32-caracteres", "cambiar-por-una-clave-larga",
            "cambiar-por-una-clave-de-12+caracteres",
            // scripts/e2e.sh
            "e2e-secret-e2e-secret-e2e-secret-e2e-secret", "e2e-partner-key", "E2e-Admin-Password-123");

    public ProductionSafetyCheck(Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (!prod && environment.acceptsProfiles(Profiles.of("dev | test"))) {
            return;
        }
        if (prod && environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("The dev profile cannot be combined with prod: it carries default secrets");
        }
        requireSecret(environment, "app.jwt.secret", "JWT_SECRET");
        requireSecret(environment, "app.partner.api-key", "PARTNER_API_KEY");
        requireSecret(environment, "app.admin.bootstrap-password", "ADMIN_BOOTSTRAP_PASSWORD");
        String partnerKey = environment.getProperty("app.partner.api-key", "");
        if (!partnerKey.isBlank() && partnerKey.length() < MIN_PARTNER_KEY_LENGTH) {
            throw new IllegalStateException("PARTNER_API_KEY must have at least " + MIN_PARTNER_KEY_LENGTH
                    + " characters (or be empty to disable the partner API)");
        }
    }

    private static void requireSecret(Environment environment, String property, String variable) {
        String value = environment.getProperty(property, "").trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (PUBLIC_VALUES.contains(value) || lower.contains("cambiar") || lower.contains("change-me")) {
            throw new IllegalStateException(variable + " still holds a public example value; set a random secret");
        }
    }
}
