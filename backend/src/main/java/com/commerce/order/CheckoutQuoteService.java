package com.commerce.order;

import com.commerce.cart.Cart;
import com.commerce.cart.CartItem;
import com.commerce.cart.CartRepositories.*;
import com.commerce.catalog.CatalogRepositories.*;
import com.commerce.catalog.Category;
import com.commerce.catalog.Product;
import com.commerce.catalog.ProductImage;
import com.commerce.catalog.ProductViewAssembler;
import com.commerce.common.util.Money;
import com.commerce.inventory.Inventory;
import com.commerce.inventory.InventoryRepositories.InventoryRepository;
import com.commerce.security.CurrentUser;
import com.commerce.shop.SettingsService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative quote: rechecks publication, stock and current prices, then computes totals on the server. */
@Service
public class CheckoutQuoteService {
    public record Item(Long productId, String sku, String title, BigDecimal unitPrice, int quantity, BigDecimal lineTotal, String imageUrl) {}
    public record Issue(Long productId, String code, String message) {}
    public record Quote(List<Item> items, BigDecimal subtotal, BigDecimal shippingAmount, BigDecimal taxAmount, BigDecimal discountAmount,
                        BigDecimal total, String currency, List<Issue> issues, boolean canCheckout) {}

    /** Internal result that also carries the resolved products for order creation. */
    public record Resolved(Quote quote, List<Product> products, List<ProductImage> primaryImages) {}

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final InventoryRepository inventory;
    private final PricingService pricing;
    private final SettingsService settings;

    public CheckoutQuoteService(CartRepository carts, CartItemRepository items, ProductRepository products, CategoryRepository categories,
                                ProductImageRepository images, InventoryRepository inventory, PricingService pricing, SettingsService settings) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.inventory = inventory;
        this.pricing = pricing;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Quote quoteForCurrentUser() {
        return resolve(CurrentUser.requireId()).quote();
    }

    @Transactional(readOnly = true)
    public Resolved resolve(Long userId) {
        Cart cart = carts.findByUserId(userId).orElse(null);
        List<CartItem> rows = cart == null ? List.of() : items.findByCartIdOrderByIdAsc(cart.getId());
        List<Item> out = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<Product> resolvedProducts = new ArrayList<>();
        List<ProductImage> primaryImages = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        String currency = settings.get("currency.code", "INR");
        for (CartItem r : rows) {
            Product p = products.findById(r.getProductId()).orElse(null);
            if (p == null) continue;
            boolean live = p.isPublished() && categories.findById(p.getCategoryId()).map(Category::isActive).orElse(false);
            if (!live) {
                issues.add(new Issue(p.getId(), "UNAVAILABLE", p.getTitle() + " is no longer available."));
                continue;
            }
            int available = inventory.findById(p.getId()).map(Inventory::available).orElse(0);
            if (available <= 0) {
                issues.add(new Issue(p.getId(), "OUT_OF_STOCK", p.getTitle() + " is out of stock."));
                continue;
            }
            if (available < r.getQuantity()) {
                issues.add(new Issue(p.getId(), "INSUFFICIENT_STOCK", "Only " + available + " of " + p.getTitle() + " left."));
                continue;
            }
            BigDecimal unit = p.effectivePrice();
            BigDecimal line = Money.scale(unit.multiply(BigDecimal.valueOf(r.getQuantity())));
            ProductImage img = images.findByProductIdOrderBySortOrderAsc(p.getId()).stream().findFirst().orElse(null);
            out.add(new Item(p.getId(), p.getSku(), p.getTitle(), unit, r.getQuantity(), line, img == null ? null : ProductViewAssembler.imageRef(img).thumbUrl()));
            resolvedProducts.add(p);
            primaryImages.add(img);
            subtotal = subtotal.add(line);
            currency = p.getCurrency();
        }
        PricingService.Totals t = pricing.compute(subtotal);
        boolean can = !out.isEmpty() && issues.isEmpty();
        return new Resolved(new Quote(out, t.subtotal(), t.shipping(), t.tax(), t.discount(), t.total(), currency, issues, can), resolvedProducts, primaryImages);
    }
}
