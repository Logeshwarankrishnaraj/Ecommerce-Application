package com.commerce.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Public and admin catalog contracts (see docs/architecture/api-overview.md). */
public final class CatalogDtos {
    private CatalogDtos() {}

    public record ImageRef(Long imageId, String url, String thumbUrl, String alt, int sortOrder, boolean primary) {}

    public record ProductCard(Long productId, String slug, String sku, String title, BigDecimal price, BigDecimal salePrice,
                              BigDecimal effectivePrice, String currency, ImageRef primaryImage, boolean inStock, String categoryName) {}

    public record AttributeView(String code, String label, String valueCode, String valueLabel) {}

    public record ProductDetail(Long productId, String slug, String sku, String title, BigDecimal price, BigDecimal salePrice,
                                BigDecimal effectivePrice, String currency, ImageRef primaryImage, boolean inStock, String categoryName,
                                String description, Long categoryId, String categorySlug, List<ImageRef> images,
                                List<AttributeView> attributes, String stockStatus, int availableQuantity, String seoTitle,
                                String seoDescription) {}

    public record CategoryNode(Long categoryId, String name, String slug, String description, List<CategoryNode> children) {}

    public record FilterValue(String valueCode, String displayLabel) {}
    public record FilterAttribute(String code, String label, List<FilterValue> values) {}
    public record PriceRange(BigDecimal min, BigDecimal max) {}
    public record FiltersResponse(List<FilterAttribute> attributes, PriceRange priceRange) {}

    // ---- admin
    public record AdminProductRow(Long productId, String sku, String title, String slug, BigDecimal price, BigDecimal salePrice,
                                  String status, String categoryName, int onHand, int reserved, int available,
                                  String primaryImageUrl, Instant updatedAt) {}

    public record AttributeInput(String code, String valueCode, String textValue) {}

    public record ProductUpsert(String sku, String title, String slug, String description, Long categoryId, BigDecimal basePrice,
                                BigDecimal salePrice, String currency, String seoTitle, String seoDescription,
                                Integer lowStockThreshold, Integer initialStock, Long version, Boolean featured,
                                List<AttributeInput> attributes) {}

    public record AdminProduct(Long productId, String sku, String title, String slug, String description, Long categoryId,
                               BigDecimal basePrice, BigDecimal salePrice, String currency, String seoTitle, String seoDescription,
                               Integer lowStockThreshold, boolean featured, List<AttributeInput> attributes, String status,
                               List<ImageRef> images, int onHand, int reserved, int available, long version,
                               Instant createdAt, Instant updatedAt) {}

    public record AdminCategory(Long categoryId, Long parentCategoryId, String name, String slug, String description,
                                boolean active, int sortOrder, long productCount) {}

    public record CategoryUpsert(Long parentCategoryId, String name, String slug, String description, Boolean active, Integer sortOrder) {}

    public record ImageOrderRequest(List<Long> imageIds) {}
    public record ImagePatchRequest(String altText, Boolean primary) {}
}
