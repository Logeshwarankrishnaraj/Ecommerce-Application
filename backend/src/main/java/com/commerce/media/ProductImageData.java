package com.commerce.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Base64 payload of one product image (full size + thumbnail). Deleted by the database when its image row is deleted. */
@Entity
@Table(name = "product_image_data")
public class ProductImageData {
    @Id
    @Column(name = "storage_key", length = 32)
    private String storageKey;
    @Column(name = "image_id", nullable = false)
    private Long imageId;
    @Column(name = "content_type", nullable = false)
    private String contentType = "image/jpeg";
    @Column(name = "image_base64", nullable = false)
    private String imageBase64;
    @Column(name = "thumb_base64", nullable = false)
    private String thumbBase64;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductImageData() {}

    public ProductImageData(String storageKey, Long imageId, String imageBase64, String thumbBase64, Instant createdAt) {
        this.storageKey = storageKey;
        this.imageId = imageId;
        this.imageBase64 = imageBase64;
        this.thumbBase64 = thumbBase64;
        this.createdAt = createdAt;
    }

    public String getStorageKey() { return storageKey; }
    public Long getImageId() { return imageId; }
    public String getContentType() { return contentType; }
    public String getImageBase64() { return imageBase64; }
    public String getThumbBase64() { return thumbBase64; }
    public Instant getCreatedAt() { return createdAt; }
}
