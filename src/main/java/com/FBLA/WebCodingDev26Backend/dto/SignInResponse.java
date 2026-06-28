package com.FBLA.WebCodingDev26Backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import java.util.UUID;

/**
 * RESPONSE DTO: the authenticated user's identity plus an auth token.
 *
 * <p>Direction: server -> client (outbound response for the sign-in endpoint).
 * Returned after a successful sign-in so the client can store the user profile
 * and the token for subsequent authenticated requests.</p>
 */
public record SignInResponse(
        // Unique identifier of the signed-in user.
        String id,
        // The user's email address.
        String email,
        // The user's full name; serialized as JSON key "full_name" (snake_case)
        // via @JsonProperty to match the client/API contract.
        @JsonProperty("full_name") String fullName,
        // The user's role (drives client-side authorization, e.g. staff/user).
        String role,
        // Bearer-style auth token to send on subsequent requests.
        String token
) {
    /**
     * Factory mapper: builds the response from an {@link AppUser} entity.
     * Copies the profile fields and generates the session token.
     */
    public static SignInResponse from(AppUser user) {
        return new SignInResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                // Demo token: "demo-token-" prefix plus the first 16 hex chars of
                // a random UUID (dashes stripped). Not a real signed/JWT token.
                "demo-token-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        );
    }
}
