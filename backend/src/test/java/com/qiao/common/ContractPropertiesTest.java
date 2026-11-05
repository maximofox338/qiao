package com.qiao.common;

import com.qiao.contract.service.ContractProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContractPropertiesTest {

    @Test
    void emptyVariablesFallBackToTheVisiblePlaceholders() {
        // APP_CONTRACTS_HK_ENTITY_NAME='' in deploy/.env binds as an empty string, not as the default
        ContractProperties empty = new ContractProperties("", "  ");
        assertThat(empty.hkEntityName()).isEqualTo(ContractProperties.HK_PLACEHOLDER);
        assertThat(empty.partnerEntityName()).isEqualTo(ContractProperties.PARTNER_PLACEHOLDER);
        ContractProperties set = new ContractProperties(" Qiao Bridge (HK) Limited ", "杭州桥梁人力资源服务有限公司");
        assertThat(set.hkEntityName()).isEqualTo("Qiao Bridge (HK) Limited");
        assertThat(set.partnerEntityName()).isEqualTo("杭州桥梁人力资源服务有限公司");
    }
}
