package com.commerce.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Payment record. Never contains card data (SRS 6.1). */

@Entity
@Table(name = "payments")
public class Payment {
    public static final String PENDING_COLLECTION = "PENDING_COLLECTION";
    public static final String COLLECTED = "COLLECTED";
    public static final String INITIATED = "INITIATED";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUNDED = "REFUNDED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;
    private Long orderId;
    private String provider;
    private String method;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String providerReference;
    private String idempotencyKey;
    private String responseMetadata;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getResponseMetadata() { return responseMetadata; }
    public void setResponseMetadata(String responseMetadata) { this.responseMetadata = responseMetadata; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
