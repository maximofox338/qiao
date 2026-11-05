package com.qiao.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a talent amount among team members (spec §12.1.8): each share is amount × pct / 100 rounded down to the
 * cent, and the first entry (the lead) receives the remaining cents, so the parts always add up to the amount.
 */
public final class ShareSplitter {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ShareSplitter() {
    }

    public static List<BigDecimal> split(BigDecimal amount, List<BigDecimal> sharePcts) {
        if (sharePcts.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> parts = new ArrayList<>(sharePcts.size());
        BigDecimal assigned = Money.ZERO;
        for (BigDecimal pct : sharePcts) {
            BigDecimal part = amount.multiply(pct).divide(HUNDRED, 2, RoundingMode.DOWN);
            parts.add(part);
            assigned = assigned.add(part);
        }
        parts.set(0, parts.getFirst().add(amount.setScale(2, RoundingMode.HALF_UP).subtract(assigned)));
        return parts;
    }
}
