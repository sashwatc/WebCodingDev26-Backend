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

/**
 * Controller for the "Proof Vault" — the sealed-evidence system staff use to adjudicate claims.
 *
 * <p>When an item is found, identifying clues can be sealed in a proof vault; a claimant must
 * supply matching evidence, and staff review that evidence against the sealed clues to approve or
 * reject the claim. This controller is unusual in that it declares full paths per method (no class
 * {@link RequestMapping}), spanning both {@code /api/items/{id}/proof-vault} and
 * {@code /api/claims/{claimId}/evidence-review}.
 *
 * <p>Collaborators: {@link ProofVaultService} holds the vault/evidence logic; both
 * {@link DemoAuthorizationService} and {@link AuthService} are used to determine the caller's
 * role (staff/admin), since these endpoints expose sealed clues and review controls.
 */
@RestController
public class ProofVaultController {
    // Service encapsulating proof-vault contents and evidence-review logic.
    private final ProofVaultService proofVaultService;
    // Role resolution / enforcement from the demo email header (staff/admin gating).
    private final DemoAuthorizationService authorizationService;
    // User lookup service, used here to detect the "staff" role by loading the user record.
    private final AuthService authService;

    /** Constructor injection of the proof-vault service and the two authorization helpers. */
    public ProofVaultController(ProofVaultService proofVaultService, DemoAuthorizationService authorizationService, AuthService authService) {
        this.proofVaultService = proofVaultService;
        this.authorizationService = authorizationService;
        this.authService = authService;
    }

    /**
     * GET {@code /api/items/{id}/proof-vault} — fetch the sealed proof vault for a found item.
     *
     * @param id path variable: the found item id whose vault to retrieve.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the {@link ProofVaultResponse} (sealed clue metadata).
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin.
     */
    @GetMapping("/api/items/{id}/proof-vault")
    public ProofVaultResponse getProofVault(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Staff review claims against sealed clues, so they need the proof vault too.
        authorizationService.requireStaffOrAdmin(userEmail);
        return proofVaultService.getProofVault(id);
    }

    /**
     * GET {@code /api/claims/{claimId}/evidence-review} — fetch the evidence-review view for a claim.
     *
     * <p>Unlike the other two endpoints this does NOT hard-require staff/admin; instead it computes
     * a {@code privileged} flag and passes it to the service so the response can be tailored: a
     * privileged (admin or staff) caller may see the full sealed comparison, while a non-privileged
     * caller (e.g. the claimant) receives a restricted view.
     *
     * @param claimId path variable: the claim being reviewed.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with an {@link EvidenceReviewResponse}, detail level depending on privilege.
     */
    @GetMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse getEvidenceReview(@PathVariable String claimId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Privileged if the caller is an admin OR a user whose stored role is "staff".
        boolean privileged = authorizationService.isAdmin(userEmail)
                || authService.findByEmail(userEmail).map(u -> "staff".equalsIgnoreCase(u.getRole())).orElse(false);
        return proofVaultService.getEvidenceReview(claimId, privileged);
    }

    /**
     * POST {@code /api/claims/{claimId}/evidence-review} — record a staff evidence-review decision.
     *
     * @param claimId path variable: the claim being adjudicated.
     * @param request request body: the review decision/notes ({@link EvidenceReviewRequest}).
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link EvidenceReviewResponse}.
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin.
     */
    @PostMapping("/api/claims/{claimId}/evidence-review")
    public EvidenceReviewResponse reviewEvidence(
            @PathVariable String claimId,
            @RequestBody EvidenceReviewRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Submitting a review verdict is a staff/admin-only action.
        authorizationService.requireStaffOrAdmin(userEmail);
        return proofVaultService.reviewEvidence(claimId, request);
    }
}
