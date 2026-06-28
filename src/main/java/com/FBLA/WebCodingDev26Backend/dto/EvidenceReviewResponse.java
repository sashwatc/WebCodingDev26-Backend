package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Response payload (direction: server -> client) presenting the full evidence-review
 * dossier for a claim to a staff reviewer. Bundles the claimant's submitted proof
 * alongside the item's private verification clues so the reviewer can compare them, and
 * surfaces any prior review assessment.
 */
public record EvidenceReviewResponse(
        // Identifier of the claim under review.
        String claimId,
        // Identifier of the found item the claim is against.
        String foundItemId,
        // Staff-only verification clues recorded on the item (the "secret" details a true
        // owner should know); not exposed to claimants.
        List<String> privateVerificationClues,
        // Checklist of evidence items/prompts the claimant was asked to address.
        List<String> evidenceChecklist,
        // Claimant's responses to the private evidence prompts, keyed by prompt/clue.
        Map<String, String> privateEvidenceResponses,
        // Free-text identifying details supplied by the claimant.
        String identifyingDetails,
        // URL of the proof photo uploaded by the claimant; may be null if none provided.
        String proofPhotoUrl,
        // Verification score from a completed review (null if not yet reviewed).
        Integer verificationScore,
        // Verification flags raised during review (empty/null if not yet reviewed).
        List<String> verificationFlags,
        // Reviewer's summary notes from a completed review (null if not yet reviewed).
        String verificationSummary
) {
}
