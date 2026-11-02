package com.commerce.security;

import com.commerce.common.exception.UnauthenticatedException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated user id always comes from the server-side security context, never from request input. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<UserPrincipal> principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && a.getPrincipal() instanceof UserPrincipal p) return Optional.of(p);
        return Optional.empty();
    }

    public static UserPrincipal require() {
        return principal().orElseThrow(() -> new UnauthenticatedException("Please sign in to continue."));
    }

    public static Long requireId() {
        return require().getUserId();
    }

    public static Optional<Long> id() {
        return principal().map(UserPrincipal::getUserId);
    }

    public static String label() {
        return principal().map(UserPrincipal::getUsername).orElse("system");
    }

    public static boolean isSuperAdmin() {
        return principal().map(p -> p.getRoles().contains("ROLE_SUPER_ADMIN")).orElse(false);
    }
}
