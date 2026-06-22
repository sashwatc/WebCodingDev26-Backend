package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;
import java.util.Map;

public record EvidenceReviewResponse(
        String claimId,
        String foundItemId,
        List<String> privateVerificationClues,
        List<String> evidenceChecklist,
        Map<String, String> privateEvidenceResponses,
        String identifyingDetails,
        String proofPhotoUrl,
        Integer verificationScore,
        List<String> verificationFlags,
        String verificationSummary
) {
}
