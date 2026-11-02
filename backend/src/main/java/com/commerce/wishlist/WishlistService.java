package com.commerce.wishlist;

import com.commerce.analytics.InteractionService;
import com.commerce.analytics.ProductInteractionEvent;
import com.commerce.catalog.CatalogDtos.ProductCard;
import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.Product;
import com.commerce.catalog.ProductViewAssembler;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.security.CurrentUser;
import com.commerce.wishlist.WishlistRepositories.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-customer wishlist; every query is scoped by the authenticated user's own wishlist. */
@Service
public class WishlistService {
    public record Item(Long productId, Instant addedAt, ProductCard product) {}
    public record View(List<Item> items) {}

    private final WishlistRepository wishlists;
    private final WishlistItemRepository items;
    private final ProductRepository products;
    private final ProductViewAssembler assembler;
    private final InteractionService interactions;
    private final Clock clock;

    public WishlistService(WishlistRepository wishlists, WishlistItemRepository items, ProductRepository products,
                           ProductViewAssembler assembler, InteractionService interactions, Clock clock) {
        this.wishlists = wishlists;
        this.items = items;
        this.products = products;
        this.assembler = assembler;
        this.interactions = interactions;
        this.clock = clock;
    }

    private Wishlist mine() {
        Long uid = CurrentUser.requireId();
        return wishlists.findByUserId(uid).orElseGet(() -> {
            Wishlist w = new Wishlist();
            w.setUserId(uid);
            w.setCreatedAt(clock.instant());
            return wishlists.save(w);
        });
    }

    @Transactional
    public View get() {
        Wishlist w = mine();
        List<WishlistItem> rows = items.findByWishlistIdOrderByAddedAtDesc(w.getId());
        Map<Long, Product> byId = products.findAllById(rows.stream().map(WishlistItem::getProductId).toList()).stream()
                .filter(Product::isPublished).collect(Collectors.toMap(Product::getId, p -> p));   // unpublished items are hidden
        Map<Long, ProductCard> cards = assembler.cards(new ArrayList<>(byId.values())).stream().collect(Collectors.toMap(ProductCard::productId, c -> c));
        List<Item> out = new ArrayList<>();
        for (WishlistItem r : rows) {
            ProductCard c = cards.get(r.getProductId());
            if (c != null) out.add(new Item(r.getProductId(), r.getAddedAt(), c));
        }
        return new View(out);
    }

    @Transactional
    public View add(Long productId) {
        Product p = products.findById(productId).filter(Product::isPublished).orElseThrow(() -> new ResourceNotFoundException("Product"));
        Wishlist w = mine();
        if (items.findByWishlistIdAndProductId(w.getId(), p.getId()).isEmpty()) {   // idempotent for duplicates
            WishlistItem i = new WishlistItem();
            i.setWishlistId(w.getId());
            i.setProductId(p.getId());
            i.setAddedAt(clock.instant());
            items.save(i);
            interactions.recordAdd(ProductInteractionEvent.WISHLIST_ADD, p.getId(), CurrentUser.requireId());   // only on a real, successful add
        }
        return get();
    }

    @Transactional
    public View remove(Long productId) {
        Wishlist w = mine();
        items.findByWishlistIdAndProductId(w.getId(), productId).ifPresent(items::delete);
        return get();
    }
}
