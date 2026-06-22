package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewRequest;
import com.FBLA.WebCodingDev26Backend.dto.EvidenceReviewResponse;
import com.FBLA.WebCodingDev26Backend.dto.ProofVaultResponse;
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

    public ProofVaultController(ProofVaultService proofVaultService, DemoAuthorizationService authorizationService) {
        this.proofVaultService = proofVaultService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/items/{id}/proof-vault")
    public ProofVaultResponse getProofVault(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return proofVaultService.getProofVault(id);
    }

    @GetMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse getEvidenceReview(@PathVariable String claimId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return proofVaultService.getEvidenceReview(claimId);
    }

    @PostMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse reviewEvidence(
            @PathVariable String claimId,
            @RequestBody EvidenceReviewRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return proofVaultService.reviewEvidence(claimId, request);
    }
}
