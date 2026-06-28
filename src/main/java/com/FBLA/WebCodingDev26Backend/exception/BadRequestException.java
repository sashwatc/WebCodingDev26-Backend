package com.FBLA.WebCodingDev26Backend.exception;

/**
 * Thrown when a request is malformed or semantically invalid (bad input from the caller).
 * It is an unchecked {@link RuntimeException}. There is no {@code @ResponseStatus} annotation here;
 * instead {@code GlobalExceptionHandler#handleBadRequest} maps it to HTTP 400 (Bad Request).
 */
public class BadRequestException extends RuntimeException {
    // Constructs the exception with a human-readable message that becomes the API error body's "message".
    public BadRequestException(String message) {
        super(message);
    }
}
