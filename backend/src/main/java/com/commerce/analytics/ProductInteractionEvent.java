package com.commerce.analytics;

import jakarta.persistence.*;
import java.time.Instant;

/** Raw interaction event. Kept separate from aggregates; only a pseudonymous key is stored (no identity). */

@Entity
@Table(name = "product_interaction_events")
public class ProductInteractionEvent {
    public static final String VIEW = "VIEW";
    public static final String WISHLIST_ADD = "WISHLIST_ADD";
    public static final String CART_ADD = "CART_ADD";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;
    private Long productId;
    private String eventType;
    private String sessionKey;
    private Long userId;
    private String dedupeKey;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
