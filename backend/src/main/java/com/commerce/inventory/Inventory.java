package com.commerce.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @Column(name = "product_id")
    private Long productId;
    private int onHand;
    private int reserved;
    private int lowStockThreshold = 5;
    @Version
    private long version;

    public int available() {
        return onHand - reserved;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getOnHand() { return onHand; }
    public void setOnHand(int onHand) { this.onHand = onHand; }
    public int getReserved() { return reserved; }
    public void setReserved(int reserved) { this.reserved = reserved; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
