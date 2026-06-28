package com.FBLA.WebCodingDev26Backend.exception;

/**
 * Thrown when the caller is authenticated but not permitted to perform the requested action.
 * It is an unchecked {@link RuntimeException}. There is no {@code @ResponseStatus} annotation here;
 * instead {@code GlobalExceptionHandler#handleForbidden} maps it to HTTP 403 (Forbidden).
 */
public class ForbiddenException extends RuntimeException {
    // Constructs the exception with a human-readable message that becomes the API error body's "message".
    public ForbiddenException(String message) {
        super(message);
    }
}
