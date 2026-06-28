package com.FBLA.WebCodingDev26Backend.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error payload (direction: server -> client) returned by exception/validation
 * handlers across the API when a request fails. Carries the HTTP status plus diagnostic
 * detail so clients can display and, for validation failures, locate field-level errors.
 */
public record ErrorResponse(
        // When the error occurred, as a UTC instant.
        Instant timestamp,
        // HTTP status code of the failed response (e.g. 400, 404, 500).
        int status,
        // Short HTTP reason phrase / error category (e.g. "Bad Request", "Not Found").
        String error,
        // Human-readable description of what went wrong.
        String message,
        // The request path/URI that produced the error.
        String path,
        // For validation failures: map of field name -> validation message; null/empty
        // when the error is not field-specific.
        Map<String, String> fieldErrors
) {
}
