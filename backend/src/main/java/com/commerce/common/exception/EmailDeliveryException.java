package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class EmailDeliveryException extends AppException {
    public EmailDeliveryException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED", "We could not send the email right now. Please try again shortly.");
    }
}
