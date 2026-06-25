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

@Service
public class ProofVaultService {
    private final FoundItemRepository foundItems;
    private final ClaimRepository claims;
    private final ClockService clock;

    public ProofVaultService(FoundItemRepository foundItems, ClaimRepository claims, ClockService clock) {
        this.foundItems = foundItems;
        this.claims = claims;
        this.clock = clock;
    }

    public ProofVaultResponse getProofVault(String foundItemId) {
        FoundItem item = foundItems.findById(foundItemId).orElseThrow(() -> new NotFoundException("Found item not found"));
        return new ProofVaultResponse(
                item.getId(),
                item.getTitle(),
                item.getPrivateVerificationClues(),
                Boolean.TRUE.equals(item.getRestrictedVisibility()),
                item.getAssetTag(),
                item.getAssetRecordId(),
                item.getDepartmentDestination(),
                item.getStorageLocation()
        );
    }

    public EvidenceReviewResponse getEvidenceReview(String claimId) {
        return getEvidenceReview(claimId, true);
    }

    public EvidenceReviewResponse getEvidenceReview(String claimId, boolean includePrivateClues) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        EvidenceReviewResponse full = response(claim, item);
        if (!includePrivateClues) {
            return new EvidenceReviewResponse(
                    full.claimId(), full.foundItemId(), null,
                    full.evidenceChecklist(), full.privateEvidenceResponses(),
                    full.identifyingDetails(), full.proofPhotoUrl(),
                    full.verificationScore(), full.verificationFlags(), full.verificationSummary()
            );
        }
        return full;
    }

    public EvidenceReviewResponse reviewEvidence(String claimId, EvidenceReviewRequest request) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        ReviewScore computed = computeScore(item, claim);
        claim.setVerificationScore(request.verificationScore() == null ? computed.score() : clamp(request.verificationScore()));
        claim.setVerificationFlags(request.verificationFlags() == null || request.verificationFlags().isEmpty()
                ? computed.flags()
                : request.verificationFlags());
        claim.setVerificationSummary(valueOrDefault(request.verificationSummary(), computed.summary()));
        claim.setUpdatedDate(clock.now());
        claims.save(claim);
        return response(claim, item);
    }

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

    private ReviewScore computeScore(FoundItem item, Claim claim) {
        Set<String> clueWords = words(String.join(" ", item.getPrivateVerificationClues()));
        String evidenceText = String.join(" ",
                safe(claim.getIdentifyingDetails()),
                safe(claim.getPrivateEvidenceResponses() == null ? "" : String.join(" ", claim.getPrivateEvidenceResponses().values())),
                safe(claim.getClaimReason())
        );
        Set<String> evidenceWords = words(evidenceText);
        clueWords.retainAll(evidenceWords);

        List<String> flags = new ArrayList<>();
        int score = 20;
        if (!clueWords.isEmpty()) {
            score += Math.min(55, clueWords.size() * 18);
            flags.add(clueWords.size() >= 2 ? "strong overlap" : "partial overlap");
        } else {
            flags.add("missing evidence");
        }
        if (claim.getProofPhotoUrl() != null && !claim.getProofPhotoUrl().isBlank()) {
            score += 15;
            flags.add("proof photo supplied");
        }
        if (claim.getEvidenceChecklist() != null && !claim.getEvidenceChecklist().isEmpty()) {
            score += Math.min(10, claim.getEvidenceChecklist().size() * 3);
        }
        return new ReviewScore(clamp(score), flags, summaryFrom(flags));
    }

    private String summaryFrom(List<String> flags) {
        if (flags.contains("strong overlap")) {
            return "Claim evidence strongly overlaps with sealed verification clues. Staff review is still required.";
        }
        if (flags.contains("partial overlap")) {
            return "Claim evidence partially overlaps with sealed verification clues. Staff should request or compare details.";
        }
        return "Claim evidence does not yet overlap with sealed verification clues. Staff review is required.";
    }

    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String part : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (part.length() > 2) {
                words.add(part);
            }
        }
        return words;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ReviewScore(int score, List<String> flags, String summary) {
    }
}
