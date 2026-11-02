package com.commerce.common.exception;

import com.commerce.common.api.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Base for expected domain errors. These are NOT alerted to admins (SRS ERR-01). */
public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final List<ApiError.FieldErrorItem> fieldErrors;

    public AppException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, List.of());
    }

    public AppException(HttpStatus status, String errorCode, String message, List<ApiError.FieldErrorItem> fieldErrors) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public List<ApiError.FieldErrorItem> getFieldErrors() { return fieldErrors; }
}
