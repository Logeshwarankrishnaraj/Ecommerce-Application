package com.commerce.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;
    private String orderNumber;
    private Long userId;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal shippingAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private String currency;
    private String shipRecipient;
    private String shipPhone;
    private String shipLine1;
    private String shipLine2;
    private String shipLocality;
    private String shipCity;
    private String shipState;
    private String shipPostalCode;
    private String shipCountry;
    private String idempotencyKey;
    private String packingNotes;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getShippingAmount() { return shippingAmount; }
    public void setShippingAmount(BigDecimal shippingAmount) { this.shippingAmount = shippingAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getShipRecipient() { return shipRecipient; }
    public void setShipRecipient(String shipRecipient) { this.shipRecipient = shipRecipient; }
    public String getShipPhone() { return shipPhone; }
    public void setShipPhone(String shipPhone) { this.shipPhone = shipPhone; }
    public String getShipLine1() { return shipLine1; }
    public void setShipLine1(String shipLine1) { this.shipLine1 = shipLine1; }
    public String getShipLine2() { return shipLine2; }
    public void setShipLine2(String shipLine2) { this.shipLine2 = shipLine2; }
    public String getShipLocality() { return shipLocality; }
    public void setShipLocality(String shipLocality) { this.shipLocality = shipLocality; }
    public String getShipCity() { return shipCity; }
    public void setShipCity(String shipCity) { this.shipCity = shipCity; }
    public String getShipState() { return shipState; }
    public void setShipState(String shipState) { this.shipState = shipState; }
    public String getShipPostalCode() { return shipPostalCode; }
    public void setShipPostalCode(String shipPostalCode) { this.shipPostalCode = shipPostalCode; }
    public String getShipCountry() { return shipCountry; }
    public void setShipCountry(String shipCountry) { this.shipCountry = shipCountry; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPackingNotes() { return packingNotes; }
    public void setPackingNotes(String packingNotes) { this.packingNotes = packingNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
