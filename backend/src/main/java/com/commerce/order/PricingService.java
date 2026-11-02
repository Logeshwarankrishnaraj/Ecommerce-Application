package com.commerce.order;

import com.commerce.common.util.Money;
import com.commerce.shop.SettingsService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Server-side totals. Tax and shipping rules come only from configuration; nothing is assumed (SRS 11.12). */
@Service
public class PricingService {
    public record Totals(BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal discount, BigDecimal total) {}

    private final SettingsService settings;

    public PricingService(SettingsService settings) {
        this.settings = settings;
    }

    public Totals compute(BigDecimal subtotal) {
        return compute(subtotal, settings.getDecimal("shipping.flatFee", BigDecimal.ZERO),
                settings.getDecimal("shipping.freeOver", BigDecimal.ZERO), settings.getDecimal("tax.ratePercent", BigDecimal.ZERO));
    }

    /** Pure function (unit tested). */
    public static Totals compute(BigDecimal subtotalIn, BigDecimal flatFee, BigDecimal freeOver, BigDecimal taxRatePercent) {
        BigDecimal subtotal = Money.scale(subtotalIn);
        BigDecimal shipping = Money.ZERO;
        if (subtotal.signum() > 0) {
            boolean free = freeOver.signum() > 0 && subtotal.compareTo(freeOver) >= 0;
            shipping = free ? Money.ZERO : Money.scale(flatFee);
        }
        BigDecimal tax = Money.percent(subtotal, taxRatePercent);
        BigDecimal discount = Money.ZERO;
        BigDecimal total = Money.scale(subtotal.add(shipping).add(tax).subtract(discount));
        return new Totals(subtotal, shipping, tax, discount, total);
    }
}
