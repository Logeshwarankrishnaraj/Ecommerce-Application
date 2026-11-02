package com.commerce.user;

import com.commerce.common.exception.BusinessRuleException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.security.CurrentUser;
import com.commerce.security.PasswordPolicy;
import com.commerce.user.UserDtos.*;
import java.time.Clock;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Own-profile and own-address operations. The user id always comes from the authenticated principal. */
@Service
public class AccountService {
    private final AppUserRepository users;
    private final UserAddressRepository addresses;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(AppUserRepository users, UserAddressRepository addresses, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.addresses = addresses;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountMe me() {
        return AccountMe.of(current());
    }

    @Transactional
    public AccountMe updateMe(UpdateProfileRequest req) {
        AppUser u = current();
        if (req.fullName() != null) u.setFullName(req.fullName().trim());
        if (req.mobileNumber() != null) u.setMobileNumber(req.mobileNumber().replaceAll("[\\s-]", ""));
        u.setUpdatedAt(clock.instant());
        return AccountMe.of(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        AppUser u = current();
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ValidationFailedException("currentPassword", "Current password is incorrect.");
        }
        PasswordPolicy.require("newPassword", req.newPassword());
        if (encoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw new BusinessRuleException("New password must differ from the current password.");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setUpdatedAt(clock.instant());
    }

    private AppUser current() {
        return users.findById(CurrentUser.requireId()).orElseThrow(() -> new ResourceNotFoundException("Account"));
    }

    // ------------------------------------------------------------ addresses

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses() {
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(CurrentUser.requireId()).stream().map(AddressResponse::of).toList();
    }

    @Transactional
    public AddressResponse createAddress(AddressRequest req) {
        Long uid = CurrentUser.requireId();
        UserAddress a = new UserAddress();
        a.setUserId(uid);
        a.setCreatedAt(clock.instant());
        apply(a, req);
        boolean first = addresses.countByUserId(uid) == 0;
        a.setDefaultAddress(first || req.isDefault());
        a = addresses.save(a);
        if (a.isDefaultAddress()) clearOtherDefaults(uid, a.getId());
        return AddressResponse.of(a);
    }

    @Transactional
    public AddressResponse updateAddress(Long addressId, AddressRequest req) {
        Long uid = CurrentUser.requireId();
        UserAddress a = addresses.findByIdAndUserId(addressId, uid).orElseThrow(() -> new ResourceNotFoundException("Address"));
        apply(a, req);
        if (req.isDefault()) {
            a.setDefaultAddress(true);
            clearOtherDefaults(uid, a.getId());
        }
        return AddressResponse.of(a);
    }

    @Transactional
    public void deleteAddress(Long addressId) {
        Long uid = CurrentUser.requireId();
        UserAddress a = addresses.findByIdAndUserId(addressId, uid).orElseThrow(() -> new ResourceNotFoundException("Address"));
        boolean wasDefault = a.isDefaultAddress();
        addresses.delete(a);
        addresses.flush();
        if (wasDefault) {
            addresses.findByUserIdOrderByDefaultAddressDescIdAsc(uid).stream().findFirst().ifPresent(n -> n.setDefaultAddress(true));
        }
    }

    private void clearOtherDefaults(Long uid, Long keepId) {
        for (UserAddress o : addresses.findByUserIdOrderByDefaultAddressDescIdAsc(uid)) {
            if (!o.getId().equals(keepId) && o.isDefaultAddress()) o.setDefaultAddress(false);
        }
    }

    private void apply(UserAddress a, AddressRequest r) {
        a.setRecipientName(r.recipientName().trim());
        a.setPhone(r.phone().replaceAll("[\\s-]", ""));
        a.setLine1(r.line1().trim());
        a.setLine2(blankToNull(r.line2()));
        a.setLocality(blankToNull(r.locality()));
        a.setCity(r.city().trim());
        a.setState(r.state().trim());
        a.setPostalCode(r.postalCode().trim());
        a.setCountry(r.country().trim());
        a.setUpdatedAt(clock.instant());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
