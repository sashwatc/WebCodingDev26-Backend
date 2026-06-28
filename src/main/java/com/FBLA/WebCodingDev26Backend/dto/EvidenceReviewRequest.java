package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

/**
 * Request payload (direction: client -> server) submitted by staff when reviewing a
 * claimant's evidence for a claim. Records the reviewer's assessment of how well the
 * provided proof verifies ownership.
 */
public record EvidenceReviewRequest(
        // Reviewer-assigned verification score (e.g. a 0-100 confidence level) for the evidence.
        Integer verificationScore,
        // List of flag labels raised during review (e.g. concerns or noteworthy markers).
        List<String> verificationFlags,
        // Free-text summary capturing the reviewer's overall verification notes.
        String verificationSummary
) {
}
