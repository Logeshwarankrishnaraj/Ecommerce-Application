package com.commerce.inventory;

import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.Product;
import com.commerce.config.AppProperties;
import com.commerce.notification.OutboxService;
import com.commerce.shop.SettingsService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Emails the admin alert list once when a product's available stock first falls to its low-stock threshold.
 * "Once" means on the downward crossing only, so a product sitting below the threshold does not send mail on every sale.
 * Mail goes through the outbox inside the caller's transaction (rolled back with the stock change if it fails).
 */
@Component
public class LowStockAlerter {
    private final OutboxService outbox;
    private final SettingsService settings;
    private final AppProperties props;
    private final ProductRepository products;

    public LowStockAlerter(OutboxService outbox, SettingsService settings, AppProperties props, ProductRepository products) {
        this.outbox = outbox;
        this.settings = settings;
        this.props = props;
        this.products = products;
    }

    /** @param availableBefore available stock (on hand minus reserved) before the change that was just applied to {@code i} */
    public void afterChange(Inventory i, int availableBefore) {
        if (!crossedDown(availableBefore, i.available(), i.getLowStockThreshold())) return;
        if (!settings.getBool("alerts.lowStockEmail", true)) return;
        List<String> recipients = props.getMail().adminAlertList();
        if (recipients.isEmpty()) return;
        Product p = products.findById(i.getProductId()).orElse(null);
        String name = p == null ? "Product #" + i.getProductId() : p.getTitle() + " (" + p.getSku() + ")";
        String base = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().replaceAll("/+$", "");
        String subject = "[Jewelry Store] Low stock: " + name;
        String body = name + " is running low.\n\nAvailable: " + i.available() + "\nOn hand: " + i.getOnHand() + "\nReserved: " + i.getReserved()
                + "\nLow-stock threshold: " + i.getLowStockThreshold() + "\n\nManage stock: " + base + "/admin/inventory";
        for (String to : recipients) outbox.enqueue("ADMIN_LOW_STOCK", to, subject, body, "PRODUCT", i.getProductId());
    }

    /** True only when stock moved from above the threshold to at or below it. */
    static boolean crossedDown(int before, int after, int threshold) {
        return before > threshold && after <= threshold;
    }
}
