package com.commerce.order;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void addsFlatShippingBelowThreshold() {
        var t = PricingService.compute(d("1000"), d("99"), d("5000"), d("0"));
        assertEquals(d("1000.00"), t.subtotal());
        assertEquals(d("99.00"), t.shipping());
        assertEquals(d("1099.00"), t.total());
    }

    @Test
    void shippingIsFreeAtOrAboveThreshold() {
        assertEquals(d("0.00"), PricingService.compute(d("5000"), d("99"), d("5000"), d("0")).shipping());
    }

    @Test
    void zeroThresholdNeverGivesFreeShipping() {
        assertEquals(d("99.00"), PricingService.compute(d("100000"), d("99"), d("0"), d("0")).shipping());
    }

    @Test
    void taxIsPercentOfSubtotal() {
        var t = PricingService.compute(d("1000"), d("0"), d("0"), d("3"));
        assertEquals(d("30.00"), t.tax());
        assertEquals(d("1030.00"), t.total());
    }

    @Test
    void emptyCartCostsNothing() {
        var t = PricingService.compute(BigDecimal.ZERO, d("99"), d("5000"), d("3"));
        assertEquals(d("0.00"), t.total());
    }
}
