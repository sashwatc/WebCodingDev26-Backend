package com.FBLA.WebCodingDev26Backend.dto;

/**
 * RESPONSE DTO: the result of verifying a return pass one-time code.
 *
 * <p>Direction: server -> client (outbound response for the return-pass verify
 * endpoint). Tells the caller whether the submitted code is valid and, when it
 * is, surfaces the associated pass/item/claim context.</p>
 */
public record ReturnPassVerifyResponse(
        // True if the submitted code maps to a usable pass; false otherwise.
        boolean valid,
        // ID of the matched return pass (null/empty when not valid).
        String returnPassId,
        // Current status of the matched pass (e.g. active/redeemed/expired).
        String status,
        // ID of the found item tied to the pass (when valid).
        String foundItemId,
        // ID of the claim tied to the pass (when valid).
        String claimId,
        // Human-readable explanation of the verification outcome (e.g. why it
        // is invalid: not found / expired / already redeemed).
        String message
) {
}
