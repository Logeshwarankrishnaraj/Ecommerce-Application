package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends AppException {
    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }
}
