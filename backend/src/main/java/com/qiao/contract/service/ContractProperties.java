package com.qiao.contract.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Legal names printed in contract documents ({@code APP_CONTRACTS_HK_ENTITY_NAME},
 * {@code APP_CONTRACTS_PARTNER_ENTITY_NAME}). Unset or empty values fall back to bracketed placeholders, which
 * {@code ProductionSafetyCheck} refuses in production (except for a {@code PAYMENT_PROVIDER=fake} test run).
 */
@ConfigurationProperties("app.contracts")
public record ContractProperties(
        @DefaultValue(HK_PLACEHOLDER) String hkEntityName,
        @DefaultValue(PARTNER_PLACEHOLDER) String partnerEntityName) {

    public static final String HK_PLACEHOLDER = "[Hong Kong entity legal name]";
    public static final String PARTNER_PLACEHOLDER = "[PRC partner legal name]";

    public ContractProperties {
        // an empty variable in deploy/.env (APP_CONTRACTS_…='') binds as "", not as the default
        hkEntityName = hkEntityName == null || hkEntityName.isBlank() ? HK_PLACEHOLDER : hkEntityName.trim();
        partnerEntityName = partnerEntityName == null || partnerEntityName.isBlank()
                ? PARTNER_PLACEHOLDER : partnerEntityName.trim();
    }
}
