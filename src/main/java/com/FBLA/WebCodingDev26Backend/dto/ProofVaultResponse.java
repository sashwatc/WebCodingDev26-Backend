package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record ProofVaultResponse(
        String foundItemId,
        String title,
        List<String> privateVerificationClues,
        Boolean restrictedVisibility,
        String assetTag,
        String assetRecordId,
        String departmentDestination,
        String storageLocation
) {
}
