package com.commerce.security;

import com.commerce.user.AppUser;
import com.commerce.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks the account on every authenticated request so that disabling a user or changing roles takes
 * effect immediately (AUTH-09), instead of waiting for the session to expire.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public SessionRevalidationFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            AppUser u = users.findById(p.getUserId()).orElse(null);
            if (u == null || !u.isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                SecurityErrors.write(req, res, 401, "UNAUTHENTICATED", "Your session has ended. Please sign in again.");
                return;
            }
            if (!u.roleCodes().equals(p.getRoles())) {
                UserPrincipal fresh = UserPrincipal.of(u);
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(ctx);
            }
        }
        chain.doFilter(req, res);
    }
}
