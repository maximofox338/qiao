package com.qiao.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * USD amounts: always 2 decimals, rounded half-up.
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
