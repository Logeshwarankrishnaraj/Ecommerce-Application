package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " was not found");
    }
}
