package com.commerce.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed application settings (prefix "app"). All secrets come from the environment. */

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String publicBaseUrl = "http://localhost:4200";
    private String corsAllowedOrigins = "http://localhost:4200";
    private Mail mail = new Mail();
    private Otp otp = new Otp();
    private Media media = new Media();
    private Payment payment = new Payment();
    private Errors errors = new Errors();
    private Seed seed = new Seed();

    public static class Mail {
        private String fromEmail = "no-reply@jewelstore.local";
        private boolean logOnly = false;
        private String adminAlertEmails = "";

        public List<String> adminAlertList() {
            List<String> out = new ArrayList<>();
            for (String s : adminAlertEmails.split(",")) {
                if (!s.isBlank()) out.add(s.trim());
            }
            return out;
        }

    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
    public boolean isLogOnly() { return logOnly; }
    public void setLogOnly(boolean logOnly) { this.logOnly = logOnly; }
    public String getAdminAlertEmails() { return adminAlertEmails; }
    public void setAdminAlertEmails(String adminAlertEmails) { this.adminAlertEmails = adminAlertEmails; }
}

    public static class Otp {
        private int ttlSeconds = 600;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
        private int maxResends = 5;

    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
    public int getMaxResends() { return maxResends; }
    public void setMaxResends(int maxResends) { this.maxResends = maxResends; }
}

    public static class Media {
        private String storagePath = "./var/media";
        private long maxFileBytes = 5_242_880L;
        private int maxDimension = 6000;

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public int getMaxDimension() { return maxDimension; }
    public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }
}

    public static class Payment {
        private String mode = "disabled";
        private boolean mockEnabled = false;
        private String webhookSecret = "";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
}

    public static class Errors {
        private int dedupeWindowMinutes = 15;
        private String alertMinSeverity = "ERROR";

    public int getDedupeWindowMinutes() { return dedupeWindowMinutes; }
    public void setDedupeWindowMinutes(int dedupeWindowMinutes) { this.dedupeWindowMinutes = dedupeWindowMinutes; }
    public String getAlertMinSeverity() { return alertMinSeverity; }
    public void setAlertMinSeverity(String alertMinSeverity) { this.alertMinSeverity = alertMinSeverity; }
}

    public static class Seed {
        private boolean demoAccounts = false;
        /** One-time first super admin for a fresh production database (leave blank afterwards). */
        private String bootstrapAdminEmail = "";
        private String bootstrapAdminPassword = "";

    public boolean isDemoAccounts() { return demoAccounts; }
    public void setDemoAccounts(boolean demoAccounts) { this.demoAccounts = demoAccounts; }
    public String getBootstrapAdminEmail() { return bootstrapAdminEmail; }
    public void setBootstrapAdminEmail(String bootstrapAdminEmail) { this.bootstrapAdminEmail = bootstrapAdminEmail; }
    public String getBootstrapAdminPassword() { return bootstrapAdminPassword; }
    public void setBootstrapAdminPassword(String bootstrapAdminPassword) { this.bootstrapAdminPassword = bootstrapAdminPassword; }
}

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(String corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }
    public Mail getMail() { return mail; }
    public void setMail(Mail mail) { this.mail = mail; }
    public Otp getOtp() { return otp; }
    public void setOtp(Otp otp) { this.otp = otp; }
    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public Errors getErrors() { return errors; }
    public void setErrors(Errors errors) { this.errors = errors; }
    public Seed getSeed() { return seed; }
    public void setSeed(Seed seed) { this.seed = seed; }
}
