package com.commerce.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request/response contracts for auth, account and addresses. */
public final class UserDtos {
    private UserDtos() {}

    public static final String MOBILE_RE = "^\\+?[0-9]{10,15}$";

    public record RegisterOtpRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.]{3,30}$", message = "3-30 letters, digits, dot or underscore") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = MOBILE_RE, message = "Enter 10-15 digits, optionally starting with +") String mobileNumber) {}

    public record ResendOtpRequest(@jakarta.validation.constraints.NotNull Long challengeId) {}

    public record VerifyRegistrationRequest(
            @jakarta.validation.constraints.NotNull Long challengeId,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6-digit code") String otp,
            @NotBlank @Size(max = 100) String password) {}

    public record OtpChallengeResponse(Long challengeId, int expiresInSeconds, int resendAfterSeconds) {}

    public record LoginRequest(@NotBlank @Size(max = 254) String usernameOrEmail, @NotBlank @Size(max = 100) String password) {}

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordResetComplete(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6-digit code") String code,
            @NotBlank @Size(max = 100) String newPassword) {}

    public record MessageResponse(String message) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 100) String currentPassword, @NotBlank @Size(max = 100) String newPassword) {}

    public record UpdateProfileRequest(
            @Size(min = 2, max = 150) String fullName,
            @Pattern(regexp = MOBILE_RE, message = "Enter 10-15 digits, optionally starting with +") String mobileNumber) {}

    public record AccountMe(Long userId, String fullName, String username, String email, String mobileNumber,
                            boolean emailVerified, List<String> roles) {
        public static AccountMe of(AppUser u) {
            return new AccountMe(u.getId(), u.getFullName(), u.getUsername(), u.getEmail(), u.getMobileNumber(),
                    u.getEmailVerifiedAt() != null, u.roleCodes().stream().sorted().toList());
        }
    }

    public record AddressRequest(
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = MOBILE_RE, message = "Enter 10-15 digits, optionally starting with +") String phone,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @Size(max = 120) String locality,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "Enter a valid postal code") String postalCode,
            @NotBlank @Size(max = 60) String country,
            boolean isDefault) {}

    public record AddressResponse(Long addressId, String recipientName, String phone, String line1, String line2, String locality,
                                  String city, String state, String postalCode, String country, boolean isDefault) {
        public static AddressResponse of(UserAddress a) {
            return new AddressResponse(a.getId(), a.getRecipientName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getLocality(),
                    a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
        }
    }
}
