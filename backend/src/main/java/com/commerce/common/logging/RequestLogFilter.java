package com.commerce.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * LOCAL/TEST ONLY: one INFO line per API call (method, path, status, time) so you can see the frontend
 * reaching the backend. Never logs bodies, query strings, cookies or credentials.
 */
@Component
@Profile({"local", "test"})
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("com.commerce.http");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long t0 = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("API {} {} -> {} ({} ms) session={}", req.getMethod(), req.getRequestURI(), res.getStatus(),
                    (System.nanoTime() - t0) / 1_000_000, req.getRequestedSessionId() != null ? "cookie" : "none");
        }
    }
}
