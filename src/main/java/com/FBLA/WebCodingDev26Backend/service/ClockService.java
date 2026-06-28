package com.FBLA.WebCodingDev26Backend.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Centralizes "current time" generation for the application. Every service that needs
 * a timestamp obtains it here so time access is consistent and can be stubbed in tests
 * rather than each class calling {@link Instant#now()} directly.
 */
@Service
public class ClockService {
    /**
     * @return the current instant as an ISO-8601 UTC string (e.g. {@code 2026-06-28T12:34:56Z}).
     *         No side effects; reflects the system clock at call time.
     */
    public String now() {
        return Instant.now().toString();
    }
}
