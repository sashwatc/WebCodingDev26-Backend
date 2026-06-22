package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record EvidenceReviewRequest(
        Integer verificationScore,
        List<String> verificationFlags,
        String verificationSummary
) {
}
