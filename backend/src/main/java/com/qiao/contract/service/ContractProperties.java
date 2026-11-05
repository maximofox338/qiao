package com.qiao.contract.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Legal names printed in contract documents ({@code APP_CONTRACTS_HK_ENTITY_NAME},
 * {@code APP_CONTRACTS_PARTNER_ENTITY_NAME}).
 */
@ConfigurationProperties("app.contracts")
public record ContractProperties(
        @DefaultValue("[Hong Kong entity legal name]") String hkEntityName,
        @DefaultValue("[PRC partner legal name]") String partnerEntityName) {
}
