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
    private static final Logger LOGGER = LoggerFactory.getLogger(AppwriteAuthService.class);

    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String projectId;
    private final String adminTeamId;
    private final HttpClient httpClient;

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
        if (!isConfigured() || jwt == null || jwt.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    accountRequest("/account", jwt),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            String id = body.path("$id").asText("");
            String email = body.path("email").asText("");
            String name = body.path("name").asText("");
            if (id.isBlank() || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AppwriteIdentity(id, email, name));
        } catch (InterruptedException exception) {
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
        if (!isAdminTeamConfigured() || !isConfigured() || jwt == null || jwt.isBlank()) {
            return false;
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    accountRequest("/teams", jwt),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode teams = objectMapper.readTree(response.body()).path("teams");
            if (!teams.isArray()) {
                return false;
            }
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
            LOGGER.warn("Appwrite team lookup failed: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record AppwriteIdentity(String id, String email, String name) {
        public String normalizedEmail() {
            return AppwriteAuthService.normalize(email);
        }
    }
}
