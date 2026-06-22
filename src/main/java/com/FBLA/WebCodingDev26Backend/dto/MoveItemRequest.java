package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MoveItemRequest(
        @NotBlank(message = "Destination is required.") String destination,
        String note,
        String photoEvidenceUrl
) {
}
