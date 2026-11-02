package com.commerce.common.exception;

import com.commerce.common.api.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends AppException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }

    public DuplicateResourceException(String field, String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message, List.of(new ApiError.FieldErrorItem(field, message)));
    }
}
