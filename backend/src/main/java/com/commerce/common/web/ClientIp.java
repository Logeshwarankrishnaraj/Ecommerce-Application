package com.commerce.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}

    /** Remote address as resolved by the container (forward headers handled by server.forward-headers-strategy). */
    public static String of(HttpServletRequest req) {
        return req.getRemoteAddr();
    }
}
