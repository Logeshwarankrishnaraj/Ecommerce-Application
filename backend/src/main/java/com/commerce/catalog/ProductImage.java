package com.commerce.catalog;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;
    private Long productId;
    private String storageKey;
    private String altText;
    private int sortOrder;
    private int width;
    private int height;
    @Column(name = "is_primary")
    private boolean primaryImage;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public boolean isPrimaryImage() { return primaryImage; }
    public void setPrimaryImage(boolean primaryImage) { this.primaryImage = primaryImage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
