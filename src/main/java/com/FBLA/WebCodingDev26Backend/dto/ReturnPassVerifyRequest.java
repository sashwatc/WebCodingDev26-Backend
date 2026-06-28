package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST DTO: payload to verify (validate without consuming) a return pass.
 *
 * <p>Direction: client -> server (inbound request body for the return-pass
 * verify endpoint). Used to check a code's validity/state prior to redemption;
 * unlike redeem, verification does not mark the pass as used.</p>
 */
public record ReturnPassVerifyRequest(
        // The one-time code to verify. Required: @NotBlank rejects
        // null/empty/whitespace-only values with the given validation message.
        @NotBlank(message = "One-time code is required.") String oneTimeCode
) {
}
