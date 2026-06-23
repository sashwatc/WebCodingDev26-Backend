package com.FBLA.WebCodingDev26Backend.dto;

public record RecoveryCenterSummary(
        long activeCases,
        long openMissions,
        long claimsAwaitingReview,
        long pickupReadyCases
) {
}
