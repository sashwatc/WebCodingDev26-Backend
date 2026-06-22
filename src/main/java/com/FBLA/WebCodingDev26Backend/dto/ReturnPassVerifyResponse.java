package com.FBLA.WebCodingDev26Backend.dto;

public record ReturnPassVerifyResponse(
        boolean valid,
        String returnPassId,
        String status,
        String foundItemId,
        String claimId,
        String message
) {
}
