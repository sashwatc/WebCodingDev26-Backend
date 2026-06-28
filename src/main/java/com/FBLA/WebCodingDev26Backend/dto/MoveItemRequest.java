package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload (direction: client -> server) submitted by staff to relocate a found
 * item to a new storage location/department, optionally attaching a note and photo
 * evidence documenting the move.
 */
public record MoveItemRequest(
        // Target destination for the item (storage location / department);
        // required and must not be blank.
        @NotBlank(message = "Destination is required.") String destination,
        // Optional free-text note describing or justifying the move.
        String note,
        // Optional URL of a photo documenting the item at/after the move.
        String photoEvidenceUrl
) {
}
