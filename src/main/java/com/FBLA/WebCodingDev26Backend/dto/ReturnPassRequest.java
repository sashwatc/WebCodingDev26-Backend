package com.FBLA.WebCodingDev26Backend.dto;

/**
 * REQUEST DTO: payload to create/issue a return pass for an approved claim.
 *
 * <p>Direction: client -> server (inbound request body for the return-pass
 * creation endpoint). Carries the staff-chosen pickup arrangements for the
 * claimant.</p>
 */
public record ReturnPassRequest(
        // Human-readable pickup window/timeframe (e.g. "Mon-Fri 9am-5pm").
        String pickupWindow,
        // Human-readable pickup location (e.g. front office / lost-and-found desk).
        String pickupLocation
) {
}
