package com.FBLA.WebCodingDev26Backend.model;

import java.util.Locale;
import java.util.Set;

public final class ItemStatus {
    public static final String FOUND = "FOUND";
    public static final String CLAIM_PENDING = "CLAIM_PENDING";
    public static final String VERIFIED = "VERIFIED";
    public static final String ARCHIVED = "ARCHIVED";

    private static final Set<String> PUBLIC_STATUSES = Set.of(
            FOUND,
            CLAIM_PENDING,
            "approved"
    );

    private static final Set<String> UNAVAILABLE_FOR_MATCHING = Set.of(
            CLAIM_PENDING,
            VERIFIED,
            ARCHIVED,
            "claimed",
            "returned",
            "archived",
            "deleted"
    );

    private ItemStatus() {
    }

    public static boolean isPubliclyVisible(String status) {
        return PUBLIC_STATUSES.contains(canonical(status));
    }

    public static boolean isUnavailableForMatching(String status) {
        return UNAVAILABLE_FOR_MATCHING.contains(canonical(status));
    }

    public static boolean isArchived(String status) {
        String normalized = canonical(status);
        return ARCHIVED.equals(normalized) || "archived".equals(normalized) || "returned".equals(normalized);
    }

    public static String canonical(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String trimmed = status.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "APPROVED", "AVAILABLE" -> FOUND;
            case "PENDING_CLAIM" -> CLAIM_PENDING;
            case "CLAIMED" -> VERIFIED;
            case "RETURNED", "COMPLETED", "RESOLVED" -> ARCHIVED;
            default -> Set.of(FOUND, CLAIM_PENDING, VERIFIED, ARCHIVED).contains(upper) ? upper : trimmed.toLowerCase(Locale.ROOT);
        };
    }
}
