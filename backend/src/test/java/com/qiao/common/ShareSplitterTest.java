package com.qiao.common;

import com.qiao.common.money.ShareSplitter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShareSplitterTest {

    private static List<BigDecimal> pcts(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void partsAreRoundedDownAndTheLeadTakesTheRemainingCents() {
        List<BigDecimal> parts = ShareSplitter.split(new BigDecimal("800.01"), pcts("60", "40"));
        assertThat(parts).containsExactly(new BigDecimal("480.01"), new BigDecimal("320.00"));

        List<BigDecimal> thirds = ShareSplitter.split(new BigDecimal("100.00"), pcts("33.34", "33.33", "33.33"));
        assertThat(thirds).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));

        List<BigDecimal> odd = ShareSplitter.split(new BigDecimal("0.05"), pcts("50", "25", "25"));
        assertThat(odd).containsExactly(new BigDecimal("0.03"), new BigDecimal("0.01"), new BigDecimal("0.01"));
    }

    @Test
    void partsAlwaysAddUpToTheAmount() {
        for (String amount : List.of("1.00", "999.99", "1234.57", "0.01", "50000.03")) {
            BigDecimal total = new BigDecimal(amount);
            List<BigDecimal> parts = ShareSplitter.split(total, pcts("12.5", "37.25", "50.25"));
            assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
            assertThat(parts).allMatch(p -> p.signum() >= 0);
        }
        assertThat(ShareSplitter.split(BigDecimal.TEN, List.of())).isEmpty();
    }
}
