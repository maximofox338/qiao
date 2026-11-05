package com.qiao.common.money;

import com.qiao.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Platform fee arithmetic (spec §6): client price = talent × (1 + fee/100), half-up to cents.
 */
@Component
public class FeeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal feePct;
    private final BigDecimal multiplier;

    public FeeCalculator(AppProperties properties) {
        this.feePct = properties.feePct();
        this.multiplier = BigDecimal.ONE.add(feePct.divide(HUNDRED, MathContext.DECIMAL64));
    }

    public BigDecimal feePct() {
        return feePct;
    }

    public BigDecimal clientAmount(BigDecimal talentAmount) {
        return talentAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal talentAmount(BigDecimal clientAmount) {
        return clientAmount.divide(multiplier, 2, RoundingMode.HALF_UP);
    }
}
