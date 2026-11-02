package com.commerce.security;

import com.commerce.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Starts a server-side session for an already-verified user, rotating the session id (anti-fixation). */
@Component
public class SessionAuthenticator {
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void start(AppUser user, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) {
            req.changeSessionId();
        } else {
            req.getSession(true);
        }
        UserPrincipal principal = UserPrincipal.of(user);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        repository.saveContext(ctx, req, res);
    }
}
