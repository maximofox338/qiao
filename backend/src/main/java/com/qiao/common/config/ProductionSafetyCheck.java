package com.qiao.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start a production deployment ({@code prod} profile, or no {@code dev}/{@code test} profile) with
 * secrets anyone can read in this repository: the {@code dev} profile (whose defaults include a JWT secret, a partner
 * key and an admin password), the placeholders of {@code deploy/.env.example} and the values of the e2e script.
 * Those values pass the length checks, so without this guard a forgotten placeholder would let anyone forge admin
 * tokens, call the partner API or sign in as the bootstrap admin.
 * <p>
 * It also refuses contract party names ({@code APP_CONTRACTS_HK_ENTITY_NAME}, {@code APP_CONTRACTS_PARTNER_ENTITY_NAME})
 * that are empty or still a bracketed placeholder ({@code [Hong Kong entity legal name]}, {@code … [dev]}): every
 * contract document is rendered with them. The one exception is a test run with {@code PAYMENT_PROVIDER=fake}, where
 * no real money can be taken and the payment page says "test mode": it starts with a warning.
 */
@Component
public class ProductionSafetyCheck {

    static final int MIN_PARTNER_KEY_LENGTH = 32;
    static final String STRIPE_API = "https://api.stripe.com";
    static final String HK_ENTITY_PROPERTY = "app.contracts.hk-entity-name";
    static final String PARTNER_ENTITY_PROPERTY = "app.contracts.partner-entity-name";

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

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
        requireSecret(environment, "app.partner.api-key-previous", "PARTNER_API_KEY_PREVIOUS");
        requireSecret(environment, "app.admin.bootstrap-password", "ADMIN_BOOTSTRAP_PASSWORD");
        // STRIPE_API_BASE exists for tests against stripe-mock; in production the secret key must only go to Stripe
        String stripeBase = environment.getProperty("app.payment.stripe-api-base", "").trim();
        if (!stripeBase.isEmpty() && !stripeBase.equals(STRIPE_API)) {
            throw new IllegalStateException("STRIPE_API_BASE must be empty (or " + STRIPE_API + ") in production");
        }
        checkPartnerKeys(environment);
        checkContractParties(environment);
    }

    /**
     * Failed partner keys are not rate limited, so both keys need enough entropy; the previous key only makes sense
     * next to a different current key (during a rotation).
     */
    private static void checkPartnerKeys(Environment environment) {
        String partnerKey = environment.getProperty("app.partner.api-key", "");
        if (!partnerKey.isBlank() && partnerKey.length() < MIN_PARTNER_KEY_LENGTH) {
            throw new IllegalStateException("PARTNER_API_KEY must have at least " + MIN_PARTNER_KEY_LENGTH
                    + " characters (or be empty to disable the partner API)");
        }
        String previousKey = environment.getProperty("app.partner.api-key-previous", "");
        if (previousKey.isBlank()) {
            return;
        }
        if (partnerKey.isBlank()) {
            throw new IllegalStateException("PARTNER_API_KEY_PREVIOUS is set but PARTNER_API_KEY is empty: the previous "
                    + "key only stays valid during a rotation to a new PARTNER_API_KEY");
        }
        if (previousKey.length() < MIN_PARTNER_KEY_LENGTH) {
            throw new IllegalStateException("PARTNER_API_KEY_PREVIOUS must have at least " + MIN_PARTNER_KEY_LENGTH
                    + " characters (or be empty once the rotation is finished)");
        }
        if (previousKey.equals(partnerKey)) {
            throw new IllegalStateException("PARTNER_API_KEY_PREVIOUS must differ from PARTNER_API_KEY "
                    + "(leave it empty when no rotation is in progress)");
        }
    }

    private static void checkContractParties(Environment environment) {
        List<String> missing = new ArrayList<>();
        if (!isLegalName(environment.getProperty(HK_ENTITY_PROPERTY))) {
            missing.add("APP_CONTRACTS_HK_ENTITY_NAME (Hong Kong company)");
        }
        if (!isLegalName(environment.getProperty(PARTNER_ENTITY_PROPERTY))) {
            missing.add("APP_CONTRACTS_PARTNER_ENTITY_NAME (mainland China partner)");
        }
        if (missing.isEmpty()) {
            return;
        }
        String which = String.join(" and ", missing);
        String provider = environment.getProperty("app.payment.provider", "stripe").trim().toLowerCase(Locale.ROOT);
        if (provider.equals("fake")) {
            log.warn("TEST MODE: {} not set (empty or a [placeholder]); contract documents show placeholders. Accepted "
                    + "only because PAYMENT_PROVIDER=fake (no real payments); the backend refuses to start like this "
                    + "with PAYMENT_PROVIDER=stripe", which);
            return;
        }
        throw new IllegalStateException(which + " must hold the registered legal name printed in every contract; "
                + "it is empty or still a placeholder in brackets (e.g. \"[dev]\"). Only a test run with "
                + "PAYMENT_PROVIDER=fake may start without it");
    }

    /**
     * A real legal name: not blank and without the brackets of the template and dev placeholders.
     */
    public static boolean isLegalName(String value) {
        return value != null && !value.isBlank() && !value.contains("[") && !value.contains("]");
    }

    private static void requireSecret(Environment environment, String property, String variable) {
        String value = environment.getProperty(property, "").trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (PUBLIC_VALUES.contains(value) || lower.contains("cambiar") || lower.contains("change-me")) {
            throw new IllegalStateException(variable + " still holds a public example value; set a random secret");
        }
    }
}
