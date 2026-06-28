package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.ReturnPass;

/**
 * RESPONSE DTO: the full view of a return pass.
 *
 * <p>Direction: server -> client (outbound response). Returned by the
 * return-pass endpoints (create / fetch) so the claimant or staff can see the
 * pass details, its one-time code, and redemption state.</p>
 */
public record ReturnPassResponse(
        // Unique identifier of the return pass.
        String id,
        // ID of the claim this pass was issued for.
        String claimId,
        // ID of the found item being returned.
        String foundItemId,
        // Email of the claimant the pass belongs to.
        String claimantEmail,
        // Human-readable pickup window/timeframe.
        String pickupWindow,
        // Human-readable pickup location.
        String pickupLocation,
        // Lifecycle status of the pass (e.g. active/redeemed/expired).
        String status,
        // The one-time code used to verify/redeem the pass.
        String oneTimeCode,
        // Timestamp the pass expires (string as stored).
        String expiresAt,
        // Timestamp the pass was redeemed, or null if not yet redeemed.
        String redeemedAt,
        // Identifier of the staff member who redeemed it, or null if unredeemed.
        String redeemedBy,
        // Timestamp the pass record was created (string as stored).
        String createdDate,
        // Timestamp the pass record was last updated (string as stored).
        String updatedDate
) {
    /**
     * Factory mapper: builds the response from a {@link ReturnPass} entity by
     * copying through its getters. Field order here must match the record
     * component order above.
     */
    public static ReturnPassResponse from(ReturnPass pass) {
        return new ReturnPassResponse(
                pass.getId(),
                pass.getClaimId(),
                pass.getFoundItemId(),
                pass.getClaimantEmail(),
                pass.getPickupWindow(),
                pass.getPickupLocation(),
                pass.getStatus(),
                pass.getOneTimeCode(),
                pass.getExpiresAt(),
                pass.getRedeemedAt(),
                pass.getRedeemedBy(),
                pass.getCreatedDate(),
                pass.getUpdatedDate()
        );
    }
}
