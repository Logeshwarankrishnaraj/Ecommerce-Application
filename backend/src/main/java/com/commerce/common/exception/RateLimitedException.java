package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends AppException {
    public RateLimitedException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Please wait a moment and try again.");
    }
}
