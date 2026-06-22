package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.ReturnPass;

public record ReturnPassResponse(
        String id,
        String claimId,
        String foundItemId,
        String claimantEmail,
        String pickupWindow,
        String pickupLocation,
        String status,
        String oneTimeCode,
        String expiresAt,
        String redeemedAt,
        String redeemedBy,
        String createdDate,
        String updatedDate
) {
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
