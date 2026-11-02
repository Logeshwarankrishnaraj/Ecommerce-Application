package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends AppException {
    public InsufficientStockException(String message) {
        super(HttpStatus.CONFLICT, "OUT_OF_STOCK", message);
    }
}
