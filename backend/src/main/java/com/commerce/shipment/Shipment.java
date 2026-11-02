package com.commerce.shipment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long id;
    private Long orderId;
    private String carrier;
    private String trackingNumber;
    private String status = "PENDING";
    private Instant shippedAt;
    private Instant deliveredAt;
    private String shipFromSnapshot;
    private String trackingNote;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getShipFromSnapshot() { return shipFromSnapshot; }
    public void setShipFromSnapshot(String shipFromSnapshot) { this.shipFromSnapshot = shipFromSnapshot; }
    public String getTrackingNote() { return trackingNote; }
    public void setTrackingNote(String trackingNote) { this.trackingNote = trackingNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
