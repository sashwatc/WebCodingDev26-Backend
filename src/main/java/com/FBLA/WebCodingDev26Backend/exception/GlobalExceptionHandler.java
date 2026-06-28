package com.FBLA.WebCodingDev26Backend.exception;

import com.FBLA.WebCodingDev26Backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized exception-to-HTTP mapping for the whole application.
 *
 * <p>{@code @RestControllerAdvice} makes this a global advice bean: each
 * {@code @ExceptionHandler} method below intercepts the named exception thrown by ANY
 * controller and converts it into a uniform {@link ErrorResponse} JSON body with the
 * appropriate HTTP status, instead of leaking a default error page or stack trace.</p>
 *
 * <p>The shared {@link #error} helper builds that response body (timestamp, status code,
 * reason phrase, message, request path, and optional per-field errors).</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Catches our BadRequestException -> HTTP 400, body message taken from the exception.
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    // Catches our NotFoundException -> HTTP 404, body message taken from the exception.
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    // Catches UnsupportedEntityException (unknown entity type) -> HTTP 404, message from the exception.
    @ExceptionHandler(UnsupportedEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedEntityException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    // Catches any IllegalArgumentException (invalid argument) -> HTTP 400, message from the exception.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    // Catches our ConflictException -> HTTP 409 (Conflict), body message taken from the exception.
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    // Catches our ForbiddenException -> HTTP 403 (Forbidden), body message taken from the exception.
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request, Map.of());
    }

    // Catches bean-validation failures on @Valid request bodies (MethodArgumentNotValidException) -> HTTP 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        // Collect each rejected field -> its validation message into an ordered map for the response body.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        // Generic top-level message "Validation failed"; the per-field details are carried in fieldErrors.
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    // Catches Spring data-layer failures (DataAccessException, e.g. DB down) -> HTTP 503, generic message
    // (the raw exception text is hidden from the client).
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable", request, Map.of());
    }

    // Catches requests for a missing static resource (NoResourceFoundException) -> HTTP 404, message from the exception.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    // Catches requests for which no controller mapping exists (NoHandlerFoundException) -> HTTP 404, message from the exception.
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    // Fallback: catches any other uncaught Exception -> HTTP 500, generic message
    // (the real exception text is not exposed to the client).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected backend error", request, Map.of());
    }

    // Shared helper that assembles the uniform ErrorResponse body and wraps it in a ResponseEntity
    // carrying the given HTTP status. Used by every handler above.
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        // Build the standard error payload: current timestamp, numeric status, status reason phrase,
        // the message, the request path that failed, and any per-field validation errors.
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        );
        // Return the body with the matching HTTP status code.
        return ResponseEntity.status(status).body(body);
    }
}
