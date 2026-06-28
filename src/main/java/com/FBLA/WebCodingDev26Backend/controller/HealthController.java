package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.HealthResponse;
import com.FBLA.WebCodingDev26Backend.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/health-check controller.
 *
 * <p>Base route: {@code /api/health} ({@link RequestMapping}). A single public, unauthenticated
 * endpoint that lets load balancers, uptime monitors, and the front-end confirm the service is
 * up. The actual status assembly (e.g. DB reachability, build info) is delegated to
 * {@link HealthService}.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    // Service that computes the application's current health/status payload.
    private final HealthService healthService;

    /** Constructor injection of the health service. */
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * GET {@code /api/health} — return the current service health snapshot.
     *
     * @return 200 OK with a {@link HealthResponse} describing service status.
     */
    @GetMapping
    public HealthResponse health() {
        return healthService.health();
    }
}
