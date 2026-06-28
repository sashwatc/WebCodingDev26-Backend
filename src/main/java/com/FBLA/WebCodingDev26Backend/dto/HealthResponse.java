package com.FBLA.WebCodingDev26Backend.dto;

/**
 * Response payload (direction: server -> client) for the service health-check endpoint.
 * Reports overall service status and database connectivity for monitoring/uptime checks.
 *
 * Components:
 *   status    - overall health status string (e.g. "UP"/"DOWN").
 *   database  - name/identifier or status of the backing database.
 *   connected - true when the database connection is currently reachable.
 */
public record HealthResponse(String status, String database, boolean connected) {
}
