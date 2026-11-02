package com.commerce.common.exception;

import com.commerce.common.api.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationFailedException extends AppException {
    public ValidationFailedException(String field, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message, List.of(new ApiError.FieldErrorItem(field, message)));
    }

    public ValidationFailedException(String message, List<ApiError.FieldErrorItem> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message, errors);
    }
}
