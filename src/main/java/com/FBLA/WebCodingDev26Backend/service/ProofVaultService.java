package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewRequest;
import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewResponse;
import com.FBLA.WebCodingDev26Backend.dto.ProofVaultResponse;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Owns the "Proof Vault" / evidence-review workflow that staff use to decide whether a
 * claimant genuinely owns a found item.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Expose the sealed, owner-only "private verification clues" attached to a found item
 *       (the secret identifying details only a true owner should know) plus its custody/asset
 *       metadata via {@link #getProofVault(String)}.</li>
 *   <li>Assemble an evidence-review view for a claim that pairs the claimant's submitted
 *       evidence against those sealed clues, optionally redacting the clues for non-staff
 *       audiences ({@link #getEvidenceReview(String, boolean)}).</li>
 *   <li>Compute a heuristic verification score / flags / summary by measuring keyword overlap
 *       between the sealed clues and the claimant's evidence, and persist a staff decision
 *       ({@link #reviewEvidence(String, EvidenceReviewRequest)}).</li>
 * </ul>
 *
 * <p>Collaborators: {@link FoundItemRepository} (sealed clues + asset metadata),
 * {@link ClaimRepository} (claimant evidence + persisted verification result), and
 * {@link ClockService} for deterministic timestamps.
 */
@Service
public class ProofVaultService {
    /** Source of found items, including their sealed private verification clues and asset/custody metadata. */
    private final FoundItemRepository foundItems;
    /** Source of claims, including the claimant's submitted evidence and the persisted verification result. */
    private final ClaimRepository claims;
    /** Supplies the current timestamp (injectable for deterministic tests). */
    private final ClockService clock;

    /**
     * Constructs the service with its repositories and clock.
     *
     * @param foundItems repository of found items (sealed clues / asset metadata)
     * @param claims     repository of claims (evidence / verification result)
     * @param clock      timestamp provider used when persisting a review
     */
    public ProofVaultService(FoundItemRepository foundItems, ClaimRepository claims, ClockService clock) {
        this.foundItems = foundItems;
        this.claims = claims;
        this.clock = clock;
    }

    /**
     * Returns the Proof Vault view for a found item: its sealed private verification clues plus
     * custody/asset metadata (asset tag, asset record id, department destination, storage location).
     *
     * @param foundItemId id of the found item to look up
     * @return a {@link ProofVaultResponse} exposing the clues and metadata
     * @throws NotFoundException if no found item exists for the id
     */
    public ProofVaultResponse getProofVault(String foundItemId) {
        // Load the item or fail; the response surfaces sealed clues plus chain-of-custody metadata.
        FoundItem item = foundItems.findById(foundItemId).orElseThrow(() -> new NotFoundException("Found item not found"));
        return new ProofVaultResponse(
                item.getId(),
                item.getTitle(),
                item.getPrivateVerificationClues(),
                // Treat a null restricted-visibility flag as "not restricted" (false).
                Boolean.TRUE.equals(item.getRestrictedVisibility()),
                item.getAssetTag(),
                item.getAssetRecordId(),
                item.getDepartmentDestination(),
                item.getStorageLocation()
        );
    }

    /**
     * Convenience overload of {@link #getEvidenceReview(String, boolean)} that includes the sealed
     * private verification clues (the staff/full view).
     *
     * @param claimId id of the claim under review
     * @return the full evidence-review response including private clues
     * @throws NotFoundException if the claim or its linked found item is missing
     */
    public EvidenceReviewResponse getEvidenceReview(String claimId) {
        return getEvidenceReview(claimId, true);
    }

    /**
     * Assembles the evidence-review view for a claim, joining the claim's submitted evidence with
     * the found item it targets.
     *
     * @param claimId            id of the claim under review
     * @param includePrivateClues when {@code false} the sealed private verification clues are
     *                            redacted (set to {@code null}) so non-staff audiences never see them
     * @return the evidence-review response, with clues either included or redacted
     * @throws NotFoundException if the claim or its linked found item cannot be found
     */
    public EvidenceReviewResponse getEvidenceReview(String claimId, boolean includePrivateClues) {
        // Resolve the claim and the found item it references; both must exist.
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        // Build the complete response (clues included), then redact below if the caller forbids clues.
        EvidenceReviewResponse full = response(claim, item);
        if (!includePrivateClues) {
            // Rebuild the response with the private clues field forced to null; all other fields pass through.
            return new EvidenceReviewResponse(
                    full.claimId(), full.foundItemId(), null,
                    full.evidenceChecklist(), full.privateEvidenceResponses(),
                    full.identifyingDetails(), full.proofPhotoUrl(),
                    full.verificationScore(), full.verificationFlags(), full.verificationSummary()
            );
        }
        return full;
    }

    /**
     * Records a staff evidence-review decision for a claim. The heuristic score/flags/summary are
     * always computed from the current evidence, but each field in the request overrides the
     * computed value when supplied, allowing staff to accept or adjust the automated assessment.
     *
     * <p>Side effects: mutates and persists the claim (verification score, flags, summary, and
     * updated timestamp).
     *
     * @param claimId id of the claim being reviewed
     * @param request optional staff overrides for score, flags, and summary
     * @return the refreshed evidence-review response reflecting the saved decision
     * @throws NotFoundException if the claim or its found item is missing
     */
    public EvidenceReviewResponse reviewEvidence(String claimId, EvidenceReviewRequest request) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        // Always recompute the automated assessment; request fields below override it where present.
        ReviewScore computed = computeScore(item, claim);
        // Score: use the staff-supplied value (clamped to 0..100) or fall back to the computed score.
        claim.setVerificationScore(request.verificationScore() == null ? computed.score() : clamp(request.verificationScore()));
        // Flags: use staff-supplied flags only if a non-empty list was provided, else the computed flags.
        claim.setVerificationFlags(request.verificationFlags() == null || request.verificationFlags().isEmpty()
                ? computed.flags()
                : request.verificationFlags());
        // Summary: use the staff text if non-blank, else the computed narrative summary.
        claim.setVerificationSummary(valueOrDefault(request.verificationSummary(), computed.summary()));
        claim.setUpdatedDate(clock.now());
        claims.save(claim);
        return response(claim, item);
    }

    /**
     * Builds an {@link EvidenceReviewResponse} from a claim and its found item, including the
     * sealed private verification clues. Pure assembly; no persistence or redaction.
     */
    private EvidenceReviewResponse response(Claim claim, FoundItem item) {
        return new EvidenceReviewResponse(
                claim.getId(),
                item.getId(),
                item.getPrivateVerificationClues(),
                claim.getEvidenceChecklist(),
                claim.getPrivateEvidenceResponses(),
                claim.getIdentifyingDetails(),
                claim.getProofPhotoUrl(),
                claim.getVerificationScore(),
                claim.getVerificationFlags(),
                claim.getVerificationSummary()
        );
    }

    /**
     * Computes the heuristic verification assessment for a claim by measuring keyword overlap
     * between the item's sealed clues and the claimant's evidence.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Tokenize the item's private verification clues into a set of significant words.</li>
     *   <li>Concatenate the claimant's identifying details, private evidence responses (map values),
     *       and free-text claim reason, then tokenize them into a word set.</li>
     *   <li>Intersect the two sets ({@code retainAll}) so {@code clueWords} now holds only the
     *       clue words the claimant also used.</li>
     *   <li>Start from a baseline score of 20 and award points / flags:
     *       <ul>
     *         <li>Any overlap: +18 per overlapping word capped at +55; flag "strong overlap"
     *             (2+ matches) or "partial overlap" (exactly 1). No overlap: flag "missing evidence".</li>
     *         <li>A non-blank proof photo URL: +15 and flag "proof photo supplied".</li>
     *         <li>A non-empty evidence checklist: +3 per item capped at +10.</li>
     *       </ul></li>
     *   <li>Clamp the total to 0..100 and derive a narrative summary from the flags.</li>
     * </ol>
     *
     * @return the computed score, flags, and summary
     */
    private ReviewScore computeScore(FoundItem item, Claim claim) {
        // Significant words drawn from the sealed clues (joined into one string first).
        Set<String> clueWords = words(String.join(" ", item.getPrivateVerificationClues()));
        // Build the claimant's combined evidence text from three sources, null-safe.
        String evidenceText = String.join(" ",
                safe(claim.getIdentifyingDetails()),
                safe(claim.getPrivateEvidenceResponses() == null ? "" : String.join(" ", claim.getPrivateEvidenceResponses().values())),
                safe(claim.getClaimReason())
        );
        Set<String> evidenceWords = words(evidenceText);
        // Intersection: clueWords is reduced to only the clue words present in the evidence.
        clueWords.retainAll(evidenceWords);

        List<String> flags = new ArrayList<>();
        // Baseline credit before any signal-based bonuses.
        int score = 20;
        if (!clueWords.isEmpty()) {
            // Reward overlap (18 points each) but cap the overlap bonus at 55.
            score += Math.min(55, clueWords.size() * 18);
            // Two or more matching clue words is "strong"; a single match is "partial".
            flags.add(clueWords.size() >= 2 ? "strong overlap" : "partial overlap");
        } else {
            // No clue words matched at all.
            flags.add("missing evidence");
        }
        if (claim.getProofPhotoUrl() != null && !claim.getProofPhotoUrl().isBlank()) {
            // A supplied proof photo adds a fixed bonus.
            score += 15;
            flags.add("proof photo supplied");
        }
        if (claim.getEvidenceChecklist() != null && !claim.getEvidenceChecklist().isEmpty()) {
            // Small bonus for checklist completeness (3 per entry, capped at 10).
            score += Math.min(10, claim.getEvidenceChecklist().size() * 3);
        }
        return new ReviewScore(clamp(score), flags, summaryFrom(flags));
    }

    /**
     * Maps the verification flags to a human-readable summary. Always emphasizes that staff review
     * is still required regardless of overlap strength.
     *
     * @param flags the computed flags
     * @return the summary sentence corresponding to the strongest overlap flag present
     */
    private String summaryFrom(List<String> flags) {
        if (flags.contains("strong overlap")) {
            return "Claim evidence strongly overlaps with sealed verification clues. Staff review is still required.";
        }
        if (flags.contains("partial overlap")) {
            return "Claim evidence partially overlaps with sealed verification clues. Staff should request or compare details.";
        }
        return "Claim evidence does not yet overlap with sealed verification clues. Staff review is required.";
    }

    /**
     * Tokenizes free text into a set of significant lowercase words: splits on any run of
     * non-alphanumeric characters and keeps only tokens longer than two characters. Uses a
     * {@link LinkedHashSet} so order is preserved and duplicates removed.
     *
     * @param value the text to tokenize (null-safe)
     * @return the de-duplicated set of significant words
     */
    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        // Lowercase, then split on any non-alphanumeric boundary; ignore short/noise tokens (<= 2 chars).
        for (String part : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (part.length() > 2) {
                words.add(part);
            }
        }
        return words;
    }

    /** Clamps a score into the inclusive 0..100 range. */
    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Returns {@code value} if non-null and non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Null-safe string accessor: converts null to an empty string. */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** Immutable carrier for a computed verification assessment (score, flags, and summary). */
    private record ReviewScore(int score, List<String> flags, String summary) {
    }
}
