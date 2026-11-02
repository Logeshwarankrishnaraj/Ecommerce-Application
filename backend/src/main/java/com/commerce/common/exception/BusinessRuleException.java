package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends AppException {
    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE", message);
    }
}
