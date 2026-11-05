package com.qiao.common;

import com.qiao.common.config.AppProperties;
import com.qiao.common.money.FeeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator fees = new FeeCalculator(new AppProperties("http://localhost", new BigDecimal("25")));

    @Test
    void clientPriceAddsTheFeeRoundedHalfUp() {
        assertThat(fees.clientAmount(new BigDecimal("800.00"))).isEqualByComparingTo("1000.00");
        assertThat(fees.clientAmount(new BigDecimal("0.10"))).isEqualByComparingTo("0.13");
        assertThat(fees.clientAmount(new BigDecimal("33.33"))).isEqualTo(new BigDecimal("41.66"));
    }

    @Test
    void talentBudgetRemovesTheFee() {
        assertThat(fees.talentAmount(new BigDecimal("2500.00"))).isEqualTo(new BigDecimal("2000.00"));
        assertThat(fees.talentAmount(new BigDecimal("100.00"))).isEqualTo(new BigDecimal("80.00"));
    }
}
