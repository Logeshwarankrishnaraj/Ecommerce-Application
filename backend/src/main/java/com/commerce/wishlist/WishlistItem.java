package com.commerce.wishlist;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wishlist_items")
public class WishlistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_item_id")
    private Long id;
    private Long wishlistId;
    private Long productId;
    private Instant addedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWishlistId() { return wishlistId; }
    public void setWishlistId(Long wishlistId) { this.wishlistId = wishlistId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
