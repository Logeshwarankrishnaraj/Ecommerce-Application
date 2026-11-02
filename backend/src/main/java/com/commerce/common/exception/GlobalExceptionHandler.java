package com.commerce.common.exception;

import com.commerce.common.api.ApiError;
import com.commerce.common.api.ApiError.FieldErrorItem;
import com.commerce.common.logging.CorrelationIdFilter;
import com.commerce.errorcenter.ErrorEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Single place that maps exceptions to the stable error body. Stack traces stay in server logs only. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorEventService errorEvents;

    public GlobalExceptionHandler(ErrorEventService errorEvents) {
        this.errorEvents = errorEvents;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> app(AppException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), req, ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldErrorItem> items = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldErrorItem(f.getField(), f.getDefaultMessage() == null ? "Invalid value" : f.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.", req, items);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<FieldErrorItem> items = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage())).toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.", req, items);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request was malformed or is missing required data.", req, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "The uploaded file is too large.", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This operation is not supported for the resource.", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.", req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in to continue.", req, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimistic(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "This record was changed by someone else. Reload and try again.", req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {} [{}]", req.getRequestURI(), CorrelationIdFilter.current());
        return build(HttpStatus.CONFLICT, "CONFLICT", "The change conflicts with existing data (for example a duplicate value).", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        String cid = CorrelationIdFilter.current();
        log.error("Unexpected error [{}] on {} {}", cid, req.getMethod(), req.getRequestURI(), ex);
        try {
            Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = pattern == null ? req.getRequestURI() : pattern.toString();
            errorEvents.record(ex, req.getMethod() + " " + route, route, cid, "ERROR");
        } catch (RuntimeException recording) {
            // Recording must never mask the original failure; keep the original in logs (SRS 8.4).
            log.error("Could not persist error event [{}]: {}", cid, recording.toString());
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again; quote reference " + cid + " if it persists.", req, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest req, List<FieldErrorItem> fields) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, req.getRequestURI(),
                CorrelationIdFilter.current(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
