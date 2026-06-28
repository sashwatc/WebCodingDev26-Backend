package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

/**
 * Response payload (direction: server -> client) reporting the result of verifying
 * the chain-of-custody / event-history integrity for a single found item. Confirms
 * whether the recorded custody events form a complete, consistent trail.
 */
public record CustodyVerificationResponse(
        // Identifier of the found item whose custody trail was verified.
        String foundItemId,
        // True when the custody chain passed verification with no integrity issues;
        // false when one or more problems were detected.
        boolean verified,
        // Number of custody/history events examined during verification.
        Integer eventCount,
        // List of human-readable problems found in the custody trail (e.g. gaps,
        // tampering, or ordering anomalies); empty when fully verified.
        List<String> issues
) {
}
