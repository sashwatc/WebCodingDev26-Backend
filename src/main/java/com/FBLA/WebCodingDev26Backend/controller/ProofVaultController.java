package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewRequest;
import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewResponse;
import com.FBLA.WebCodingDev26Backend.dto.ProofVaultResponse;
import com.FBLA.WebCodingDev26Backend.service.AuthService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.ProofVaultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProofVaultController {
    private final ProofVaultService proofVaultService;
    private final DemoAuthorizationService authorizationService;
    private final AuthService authService;

    public ProofVaultController(ProofVaultService proofVaultService, DemoAuthorizationService authorizationService, AuthService authService) {
        this.proofVaultService = proofVaultService;
        this.authorizationService = authorizationService;
        this.authService = authService;
    }

    @GetMapping("/api/items/{id}/proof-vault")
    public ProofVaultResponse getProofVault(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Staff review claims against sealed clues, so they need the proof vault too.
        authorizationService.requireStaffOrAdmin(userEmail);
        return proofVaultService.getProofVault(id);
    }

    @GetMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse getEvidenceReview(@PathVariable String claimId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        boolean privileged = authorizationService.isAdmin(userEmail)
                || authService.findByEmail(userEmail).map(u -> "staff".equalsIgnoreCase(u.getRole())).orElse(false);
        return proofVaultService.getEvidenceReview(claimId, privileged);
    }

    @PostMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse reviewEvidence(
            @PathVariable String claimId,
            @RequestBody EvidenceReviewRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return proofVaultService.reviewEvidence(claimId, request);
    }
}
