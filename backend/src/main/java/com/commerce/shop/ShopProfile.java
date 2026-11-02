package com.commerce.shop;

import jakarta.persistence.*;
import java.time.Instant;

/** Single-row business profile (id is always 1). */

@Entity
@Table(name = "shop_profiles")
public class ShopProfile {
    @Id
    @Column(name = "shop_id")
    private Long id = 1L;
    private String displayName;
    private String tagline;
    private String logoUrl;
    private String supportEmail;
    private String supportPhone;
    private String senderDisplayName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String returnAddress;
    private int handlingTimeDays = 2;
    private Instant updatedAt;

    public String shipFromLine() {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{displayName, addressLine1, addressLine2, city, state, postalCode, country}) {
            if (s != null && !s.isBlank()) { if (sb.length() > 0) sb.append(", "); sb.append(s.trim()); }
        }
        return sb.length() > 600 ? sb.substring(0, 600) : sb.toString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }
    public String getSupportPhone() { return supportPhone; }
    public void setSupportPhone(String supportPhone) { this.supportPhone = supportPhone; }
    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getReturnAddress() { return returnAddress; }
    public void setReturnAddress(String returnAddress) { this.returnAddress = returnAddress; }
    public int getHandlingTimeDays() { return handlingTimeDays; }
    public void setHandlingTimeDays(int handlingTimeDays) { this.handlingTimeDays = handlingTimeDays; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
