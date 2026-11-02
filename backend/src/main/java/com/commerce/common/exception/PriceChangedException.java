package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class PriceChangedException extends AppException {
    public PriceChangedException(String message) {
        super(HttpStatus.CONFLICT, "PRICE_CHANGED", message);
    }
}
