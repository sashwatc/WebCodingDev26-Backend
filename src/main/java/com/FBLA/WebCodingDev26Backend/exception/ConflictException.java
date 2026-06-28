package com.FBLA.WebCodingDev26Backend.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (e.g. a duplicate or an illegal state transition). It is an unchecked {@link RuntimeException}.
 * There is no {@code @ResponseStatus} annotation here; instead
 * {@code GlobalExceptionHandler#handleConflict} maps it to HTTP 409 (Conflict).
 */
public class ConflictException extends RuntimeException {
    // Constructs the exception with a human-readable message that becomes the API error body's "message".
    public ConflictException(String message) {
        super(message);
    }
}
