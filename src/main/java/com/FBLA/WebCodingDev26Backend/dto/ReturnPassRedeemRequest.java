package com.FBLA.WebCodingDev26Backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST DTO: payload to redeem (finalize pickup of) a return pass.
 *
 * <p>Direction: client -> server (inbound request body for the return-pass
 * redeem endpoint). Submitted by staff at handoff to mark the pass as used and
 * complete the item return.</p>
 */
public record ReturnPassRedeemRequest(
        // The one-time code printed on / shown for the return pass. Required:
        // @NotBlank rejects null/empty/whitespace-only values with the given
        // validation message.
        @NotBlank(message = "One-time code is required.") String oneTimeCode
) {
}
