package com.commerce.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registration_challenges")
public class RegistrationChallenge {
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String EXPIRED = "EXPIRED";
    public static final String LOCKED = "LOCKED";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_id")
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String mobileNumber;
    private String otpHash;
    private Instant expiresAt;
    private int attempts;
    private int resendCount;
    private Instant lastSentAt;
    private String status = PENDING;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getResendCount() { return resendCount; }
    public void setResendCount(int resendCount) { this.resendCount = resendCount; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
