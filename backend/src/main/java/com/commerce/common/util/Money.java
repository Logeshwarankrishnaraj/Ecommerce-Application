package com.commerce.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is always BigDecimal with scale 2 (SRS 7.1). */
public final class Money {
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {}

    public static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal base, BigDecimal ratePercent) {
        return base.multiply(ratePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
