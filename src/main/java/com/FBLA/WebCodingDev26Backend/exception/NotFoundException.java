package com.FBLA.WebCodingDev26Backend.exception;

/**
 * Thrown when a requested resource does not exist. It is an unchecked {@link RuntimeException}.
 * There is no {@code @ResponseStatus} annotation here; instead
 * {@code GlobalExceptionHandler#handleNotFound} maps it to HTTP 404 (Not Found).
 */
public class NotFoundException extends RuntimeException {
    // Constructs the exception with a human-readable message that becomes the API error body's "message".
    public NotFoundException(String message) {
        super(message);
    }
}
