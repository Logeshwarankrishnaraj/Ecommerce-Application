package com.commerce.cart;

import com.commerce.analytics.InteractionService;
import com.commerce.analytics.ProductInteractionEvent;
import com.commerce.cart.CartRepositories.*;
import com.commerce.catalog.CatalogRepositories.CategoryRepository;
import com.commerce.catalog.CatalogRepositories.ProductImageRepository;
import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.Category;
import com.commerce.catalog.Product;
import com.commerce.catalog.ProductImage;
import com.commerce.catalog.ProductViewAssembler;
import com.commerce.common.exception.*;
import com.commerce.common.util.Money;
import com.commerce.inventory.Inventory;
import com.commerce.inventory.InventoryRepositories.InventoryRepository;
import com.commerce.security.CurrentUser;
import com.commerce.shop.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer cart. The cart is advisory: availability and prices are recomputed on every read and again,
 * authoritatively, at checkout (SRS 7.3).
 */
@Service
public class CartService {
    public record Line(Long cartItemId, Long productId, String slug, String title, String sku, String imageUrl, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal, boolean available, int availableQuantity, String issue) {}
    public record View(List<Line> items, BigDecimal subtotal, int itemCount, boolean hasIssues, String currency) {}

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final InventoryRepository inventory;
    private final SettingsService settings;
    private final InteractionService interactions;
    private final Clock clock;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products, CategoryRepository categories,
                       ProductImageRepository images, InventoryRepository inventory, SettingsService settings,
                       InteractionService interactions, Clock clock) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.inventory = inventory;
        this.settings = settings;
        this.interactions = interactions;
        this.clock = clock;
    }

    private Cart mine() {
        Long uid = CurrentUser.requireId();
        return carts.findByUserId(uid).orElseGet(() -> {
            Cart c = new Cart();
            c.setUserId(uid);
            c.setCreatedAt(clock.instant());
            c.setUpdatedAt(clock.instant());
            return carts.save(c);
        });
    }

    public Cart cartForUser(Long userId) {
        return carts.findByUserId(userId).orElse(null);
    }

    @Transactional
    public View get() {
        return view(mine());
    }

    @Transactional
    public View add(Long productId, int quantity) {
        int max = settings.getInt("cart.maxQty", 10);
        if (quantity < 1 || quantity > max) throw new ValidationFailedException("quantity", "Quantity must be between 1 and " + max + ".");
        Product p = sellable(productId);
        Cart cart = mine();
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
        int target = (item == null ? 0 : item.getQuantity()) + quantity;
        if (target > max) throw new BusinessRuleException("You can add at most " + max + " of one item.");
        int available = availableOf(productId);
        if (available < target) throw new InsufficientStockException(available <= 0 ? "This item is out of stock." : "Only " + available + " left in stock.");
        if (item == null) {
            item = new CartItem();
            item.setCartId(cart.getId());
            item.setProductId(productId);
            item.setAddedAt(clock.instant());
        }
        item.setQuantity(target);
        item.setUpdatedAt(clock.instant());
        items.save(item);
        cart.setUpdatedAt(clock.instant());
        interactions.recordAdd(ProductInteractionEvent.CART_ADD, p.getId(), CurrentUser.requireId());   // counted only after success
        return view(cart);
    }

    @Transactional
    public View updateQuantity(Long cartItemId, int quantity) {
        int max = settings.getInt("cart.maxQty", 10);
        if (quantity < 1 || quantity > max) throw new ValidationFailedException("quantity", "Quantity must be between 1 and " + max + ".");
        Cart cart = mine();
        CartItem item = items.findByIdAndCartId(cartItemId, cart.getId()).orElseThrow(() -> new ResourceNotFoundException("Cart item"));
        int available = availableOf(item.getProductId());
        if (available < quantity) throw new InsufficientStockException(available <= 0 ? "This item is out of stock." : "Only " + available + " left in stock.");
        item.setQuantity(quantity);
        item.setUpdatedAt(clock.instant());
        cart.setUpdatedAt(clock.instant());
        return view(cart);
    }

    @Transactional
    public View remove(Long cartItemId) {
        Cart cart = mine();
        items.findByIdAndCartId(cartItemId, cart.getId()).ifPresent(items::delete);
        cart.setUpdatedAt(clock.instant());
        return view(cart);
    }

    /** Used by checkout after an order is committed. */
    @Transactional
    public void clearFor(Long userId) {
        carts.findByUserId(userId).ifPresent(c -> items.deleteByCartId(c.getId()));
    }

    private Product sellable(Long productId) {
        Product p = products.findById(productId).filter(Product::isPublished).orElseThrow(() -> new ResourceNotFoundException("Product"));
        categories.findById(p.getCategoryId()).filter(Category::isActive).orElseThrow(() -> new ResourceNotFoundException("Product"));
        return p;
    }

    private int availableOf(Long productId) {
        return inventory.findById(productId).map(Inventory::available).orElse(0);
    }

    private View view(Cart cart) {
        List<CartItem> rows = items.findByCartIdOrderByIdAsc(cart.getId());
        List<Line> lines = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        int count = 0;
        boolean issues = false;
        String currency = settings.get("currency.code", "INR");
        for (CartItem r : rows) {
            Product p = products.findById(r.getProductId()).orElse(null);
            if (p == null) continue;
            boolean live = p.isPublished() && categories.findById(p.getCategoryId()).map(Category::isActive).orElse(false);
            int available = live ? Math.max(availableOf(p.getId()), 0) : 0;
            String issue = null;
            if (!live) issue = "No longer available";
            else if (available <= 0) issue = "Out of stock";
            else if (available < r.getQuantity()) issue = "Only " + available + " left";
            boolean ok = issue == null;
            BigDecimal unit = p.effectivePrice();
            BigDecimal line = Money.scale(unit.multiply(BigDecimal.valueOf(r.getQuantity())));
            ProductImage img = images.findByProductIdOrderBySortOrderAsc(p.getId()).stream().findFirst().orElse(null);
            lines.add(new Line(r.getId(), p.getId(), p.getSlug(), p.getTitle(), p.getSku(), img == null ? null : ProductViewAssembler.imageRef(img).thumbUrl(),
                    unit, r.getQuantity(), line, ok, Math.min(available, 10), issue));
            if (ok) subtotal = subtotal.add(line);
            else issues = true;
            count += r.getQuantity();
            currency = p.getCurrency();
        }
        return new View(lines, Money.scale(subtotal), count, issues, currency);
    }
}
