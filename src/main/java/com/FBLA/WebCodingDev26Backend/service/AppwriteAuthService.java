package com.FBLA.WebCodingDev26Backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies Appwrite browser sessions server-side using the safest approach that
 * needs no Appwrite server API key and no extra dependencies: the short-lived
 * account JWT minted in the browser is replayed to Appwrite's own {@code /account}
 * endpoint. Appwrite validates the signature/expiry for us, so the backend never
 * trusts a client-asserted identity or role.
 *
 * <p>No tokens are logged. This service performs only read calls and never stores
 * passwords.
 */
@Service
public class AppwriteAuthService {
    /** Logger; only failure categories are logged — never tokens or response bodies. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppwriteAuthService.class);

    /** Jackson mapper for parsing Appwrite JSON responses. */
    private final ObjectMapper objectMapper;
    /** Appwrite API base URL (trailing slashes trimmed); blank means Appwrite is not configured. */
    private final String endpoint;
    /** Appwrite project id sent in the {@code X-Appwrite-Project} header; blank means not configured. */
    private final String projectId;
    /** Id of the Appwrite team whose members are treated as admins; blank disables team-based admin checks. */
    private final String adminTeamId;
    /** Shared HTTP client (5s connect timeout) used for all Appwrite calls. */
    private final HttpClient httpClient;

    /**
     * Injects config values and builds the HTTP client. Normalizes the endpoint
     * (trim + strip trailing slashes) and trims the project/team ids. No network calls.
     */
    public AppwriteAuthService(
            ObjectMapper objectMapper,
            @Value("${app.appwrite.endpoint:}") String endpoint,
            @Value("${app.appwrite.project-id:}") String projectId,
            @Value("${app.appwrite.admin-team-id:}") String adminTeamId
    ) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint.trim().replaceAll("/+$", "");
        this.projectId = projectId == null ? "" : projectId.trim();
        this.adminTeamId = adminTeamId == null ? "" : adminTeamId.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** True when an Appwrite project is configured and JWT verification can run. */
    public boolean isConfigured() {
        return !endpoint.isBlank() && !projectId.isBlank();
    }

    /** True when admin membership can be derived from an Appwrite team. */
    public boolean isAdminTeamConfigured() {
        return !adminTeamId.isBlank();
    }

    /**
     * Validates the JWT against Appwrite and returns the verified account identity,
     * or empty when the token is missing, invalid, or expired.
     */
    public Optional<AppwriteIdentity> verify(String jwt) {
        // Short-circuit if Appwrite isn't configured or no token was supplied.
        if (!isConfigured() || jwt == null || jwt.isBlank()) {
            return Optional.empty();
        }
        try {
            // Replay the browser JWT to Appwrite's own /account endpoint; Appwrite
            // validates the signature/expiry, so we never trust client-asserted identity.
            HttpResponse<String> response = httpClient.send(
                    accountRequest("/account", jwt),
                    HttpResponse.BodyHandlers.ofString()
            );
            // Any non-200 (invalid/expired token, etc.) means "not verified".
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            // Pull the verified identity straight from Appwrite's response.
            JsonNode body = objectMapper.readTree(response.body());
            String id = body.path("$id").asText("");
            String email = body.path("email").asText("");
            String name = body.path("name").asText("");
            // Require at least an id and email to consider the identity usable.
            if (id.isBlank() || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AppwriteIdentity(id, email, name));
        } catch (InterruptedException exception) {
            // Restore interrupt status and treat as unverified.
            Thread.currentThread().interrupt();
            LOGGER.warn("Appwrite verification interrupted.");
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException exception) {
            // Do not log the token or response body; only the failure category.
            LOGGER.warn("Appwrite session verification failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Returns true when the JWT owner belongs to the configured admin team.
     * Returns false when no admin team is configured or the lookup fails.
     */
    public boolean isMemberOfAdminTeam(String jwt) {
        // Require both an admin team and base Appwrite config, plus a token.
        if (!isAdminTeamConfigured() || !isConfigured() || jwt == null || jwt.isBlank()) {
            return false;
        }
        try {
            // Ask Appwrite for the JWT owner's team memberships.
            HttpResponse<String> response = httpClient.send(
                    accountRequest("/teams", jwt),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                return false;
            }
            // Expect a "teams" array; bail if absent/malformed.
            JsonNode teams = objectMapper.readTree(response.body()).path("teams");
            if (!teams.isArray()) {
                return false;
            }
            // Admin iff the configured admin team id appears among the memberships.
            for (JsonNode team : teams) {
                if (adminTeamId.equals(team.path("$id").asText(""))) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | java.io.IOException exception) {
            // Never log the token; record only the failure class. Lookup failure = not admin.
            LOGGER.warn("Appwrite team lookup failed: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Triggers Appwrite's password recovery flow for the given email.
     * This is a best-effort call — failures are logged but not propagated.
     */
    public void triggerPasswordRecovery(String email) {
        // No-op when Appwrite isn't configured or no email is given.
        if (!isConfigured() || email == null || email.isBlank()) {
            return;
        }
        try {
            // Build the recovery request body; strip quotes from the email to avoid breaking the JSON.
            String json = "{\"email\":\"" + email.replace("\"", "") + "\",\"url\":\"" + endpoint + "/recovery\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/account/recovery"))
                    .timeout(Duration.ofSeconds(6))
                    .header("X-Appwrite-Project", projectId)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            // Fire-and-forget: the response is not inspected (best-effort).
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | java.io.IOException exception) {
            // Best-effort: swallow and log only the failure class.
            LOGGER.warn("Password recovery trigger failed: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * Builds a GET request to {@code endpoint + path} carrying the Appwrite project
     * header and the user's JWT (6s timeout), used for the verify/teams lookups.
     */
    private HttpRequest accountRequest(String path, String jwt) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(6))
                .header("X-Appwrite-Project", projectId)
                .header("X-Appwrite-JWT", jwt)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    /** Null-safe trim + lowercase (root locale) used to canonicalize emails. */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Server-verified Appwrite account identity (id, email, name).
     */
    public record AppwriteIdentity(String id, String email, String name) {
        /** @return the email normalized to trimmed lowercase, for case-insensitive matching. */
        public String normalizedEmail() {
            return AppwriteAuthService.normalize(email);
        }
    }
}
