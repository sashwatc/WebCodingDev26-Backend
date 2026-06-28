package com.FBLA.WebCodingDev26Backend.exception;

/**
 * Thrown when a request references an entity type/name the backend does not support
 * (e.g. an unknown collection name in a generic endpoint). It is an unchecked {@link RuntimeException}.
 * There is no {@code @ResponseStatus} annotation here; instead
 * {@code GlobalExceptionHandler#handleUnsupported} maps it to HTTP 404 (Not Found).
 */
public class UnsupportedEntityException extends RuntimeException {
    // Constructs the exception from the offending entity name, building the message
    // "Entity not found: <entityName>" which becomes the API error body's "message".
    public UnsupportedEntityException(String entityName) {
        super("Entity not found: " + entityName);
    }
}
