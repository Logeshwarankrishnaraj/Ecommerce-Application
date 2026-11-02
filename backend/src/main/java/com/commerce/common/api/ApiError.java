package com.commerce.common.api;

import java.time.Instant;
import java.util.List;

/** Stable error body for every non-2xx response (SRS 8.1 / api-overview). */
public record ApiError(Instant timestamp, int status, String errorCode, String message, String path,
                       String correlationId, List<FieldErrorItem> fieldErrors) {
    public record FieldErrorItem(String field, String message) {}
}
