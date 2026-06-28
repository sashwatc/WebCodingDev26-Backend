package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

/**
 * Response payload (direction: server -> client) exposing the staff-only "proof vault"
 * for a found item: the protected verification clues and custody/asset metadata used to
 * validate ownership claims. Intended for authorized staff, not public consumption.
 */
public record ProofVaultResponse(
        // Identifier of the found item this vault belongs to.
        String foundItemId,
        // Display title/name of the found item.
        String title,
        // Staff-only verification clues (secret identifying details a true owner should know).
        List<String> privateVerificationClues,
        // Whether the item's visibility is restricted (hidden from public listings).
        Boolean restrictedVisibility,
        // Asset tag associated with the item, if it is a tracked asset.
        String assetTag,
        // Identifier of the matched asset registry record, if any.
        String assetRecordId,
        // Department the item is destined for / owned by.
        String departmentDestination,
        // Physical storage location where the item is currently held.
        String storageLocation
) {
}
