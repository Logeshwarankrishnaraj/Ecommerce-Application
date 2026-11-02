package com.commerce.inventory;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable stock history row (SRS 6.1 inventory_movements). */

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;
    private Long productId;
    private int quantityDelta;
    private String reason;
    private String referenceType;
    private String referenceId;
    private String actor;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(int quantityDelta) { this.quantityDelta = quantityDelta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
