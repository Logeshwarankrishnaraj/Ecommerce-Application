package com.commerce.security;

import com.commerce.common.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Writes the standard ApiError JSON for failures raised inside the security filter chain. */
public final class SecurityErrors {
    private SecurityErrors() {}

    public static void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status + ",\"errorCode\":\"" + code
                + "\",\"message\":\"" + message.replace("\"", "'") + "\",\"path\":\"" + escape(req.getRequestURI())
                + "\",\"correlationId\":\"" + CorrelationIdFilter.current() + "\",\"fieldErrors\":[]}";
        res.getWriter().write(body);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
