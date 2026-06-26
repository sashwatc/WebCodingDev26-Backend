package com.FBLA.WebCodingDev26Backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import java.util.UUID;

public record SignInResponse(
        String id,
        String email,
        @JsonProperty("full_name") String fullName,
        String role,
        String token
) {
    public static SignInResponse from(AppUser user) {
        return new SignInResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                "demo-token-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        );
    }
}
