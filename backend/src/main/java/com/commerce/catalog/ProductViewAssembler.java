package com.commerce.catalog;

import com.commerce.catalog.CatalogDtos.ImageRef;
import com.commerce.catalog.CatalogDtos.ProductCard;
import com.commerce.catalog.CatalogRepositories.*;
import com.commerce.inventory.Inventory;
import com.commerce.inventory.InventoryRepositories.InventoryRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds card/image views with batched lookups (avoids N+1 queries on listing pages). */
@Component
public class ProductViewAssembler {
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final InventoryRepository inventory;

    public ProductViewAssembler(CategoryRepository categories, ProductImageRepository images, InventoryRepository inventory) {
        this.categories = categories;
        this.images = images;
        this.inventory = inventory;
    }

    public static ImageRef imageRef(ProductImage i) {
        return new ImageRef(i.getId(), "/media/" + i.getStorageKey() + ".jpg", "/media/" + i.getStorageKey() + "_t.jpg",
                i.getAltText(), i.getSortOrder(), i.isPrimaryImage());
    }

    public List<ProductCard> cards(List<Product> products) {
        if (products.isEmpty()) return List.of();
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, String> catNames = categories.findAllById(products.stream().map(Product::getCategoryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, ProductImage> primary = new HashMap<>();
        for (ProductImage i : images.findByProductIdInOrderBySortOrderAsc(ids)) primary.putIfAbsent(i.getProductId(), i);
        Map<Long, Inventory> inv = inventory.findByProductIdIn(ids).stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        List<ProductCard> out = new ArrayList<>();
        for (Product p : products) {
            Inventory i = inv.get(p.getId());
            ProductImage pi = primary.get(p.getId());
            out.add(new ProductCard(p.getId(), p.getSlug(), p.getSku(), p.getTitle(), p.getBasePrice(), p.getSalePrice(), p.effectivePrice(),
                    p.getCurrency(), pi == null ? null : imageRef(pi), i != null && i.available() > 0, catNames.get(p.getCategoryId())));
        }
        return out;
    }

    public ProductCard card(Product p) {
        return cards(List.of(p)).get(0);
    }
}
