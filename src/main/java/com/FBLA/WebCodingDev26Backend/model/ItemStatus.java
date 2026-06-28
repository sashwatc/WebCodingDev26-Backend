package com.FBLA.WebCodingDev26Backend.model;

import java.util.Locale;
import java.util.Set;

/**
 * ItemStatus is a non-instantiable utility/constants holder (not a persisted entity) that defines
 * the canonical lifecycle statuses for a {@link FoundItem} and helpers to normalize and classify
 * the many free-form status strings that exist in stored data.
 *
 * Canonical statuses are FOUND -> CLAIM_PENDING -> VERIFIED -> ARCHIVED. Legacy/alias strings
 * (e.g. "approved", "claimed", "returned") are mapped onto these via {@link #canonical(String)}.
 */
public final class ItemStatus {
    // Canonical status: item is in inventory and available to be claimed/matched.
    public static final String FOUND = "FOUND";
    // Canonical status: a claim has been submitted and is pending review.
    public static final String CLAIM_PENDING = "CLAIM_PENDING";
    // Canonical status: a claim was verified/approved (item assigned to its owner).
    public static final String VERIFIED = "VERIFIED";
    // Canonical status: item is closed out (returned/resolved) and no longer active.
    public static final String ARCHIVED = "ARCHIVED";

    // Status values considered visible on the public portal (canonical FOUND/CLAIM_PENDING plus legacy "approved").
    private static final Set<String> PUBLIC_STATUSES = Set.of(
            FOUND,
            CLAIM_PENDING,
            "approved"
    );

    // Status values for which an item should NOT be offered as a match candidate (already spoken for/closed).
    private static final Set<String> UNAVAILABLE_FOR_MATCHING = Set.of(
            CLAIM_PENDING,
            VERIFIED,
            ARCHIVED,
            "claimed",
            "returned",
            "archived",
            "deleted"
    );

    // Private constructor prevents instantiation of this static-only utility class.
    private ItemStatus() {
    }

    // Returns true if the (normalized) status means the item should be shown publicly.
    public static boolean isPubliclyVisible(String status) {
        return PUBLIC_STATUSES.contains(canonical(status));
    }

    // Returns true if the (normalized) status means the item is unavailable for match suggestions.
    public static boolean isUnavailableForMatching(String status) {
        return UNAVAILABLE_FOR_MATCHING.contains(canonical(status));
    }

    // Returns true if the status represents an archived/returned (closed) item, tolerating legacy spellings.
    public static boolean isArchived(String status) {
        String normalized = canonical(status);
        return ARCHIVED.equals(normalized) || "archived".equals(normalized) || "returned".equals(normalized);
    }

    // Normalizes any raw status string into a canonical form:
    //  - null/blank -> "" (empty)
    //  - known aliases are mapped to FOUND/CLAIM_PENDING/VERIFIED/ARCHIVED
    //  - an already-canonical upper-case value is kept; anything else falls back to lower-cased text.
    public static String canonical(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String trimmed = status.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "APPROVED", "AVAILABLE" -> FOUND;       // legacy "available/approved" == in-inventory FOUND
            case "PENDING_CLAIM" -> CLAIM_PENDING;        // legacy spelling of a pending claim
            case "CLAIMED" -> VERIFIED;                   // legacy "claimed" == VERIFIED ownership
            case "RETURNED", "COMPLETED", "RESOLVED" -> ARCHIVED; // any terminal state == ARCHIVED
            default -> Set.of(FOUND, CLAIM_PENDING, VERIFIED, ARCHIVED).contains(upper) ? upper : trimmed.toLowerCase(Locale.ROOT);
        };
    }
}
