package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassResponse;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyResponse;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.ReturnPassService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for "Return Passes" — the pickup tokens issued once a claim is approved.
 *
 * <p>A return pass is created against an approved claim and carries a code the claimant presents
 * (and staff verify/redeem) to physically collect their item. This controller declares full paths
 * per method (no class-level {@link RequestMapping}), under {@code /api/claims/...} and
 * {@code /api/return-passes/...}. Creating, redeeming, and reminding are staff/admin-only;
 * verifying a code and fetching a pass are open to the claimant flow (the service applies
 * finer-grained access on fetch).
 *
 * <p>Collaborators: {@link ReturnPassService} holds the issuance/redemption logic;
 * {@link ReturnPassRepository} is used directly to resolve a pass by id or by claim id;
 * {@link DemoAuthorizationService} resolves/enforces the caller's staff/admin role.
 */
@RestController
public class ReturnPassController {
    // Service encapsulating return-pass creation, verification, redemption and reminders.
    private final ReturnPassService returnPassService;
    // Direct repository access used to look up a pass by its own id or by its claim id.
    private final ReturnPassRepository returnPassRepository;
    // Resolves the caller's role from the demo email header and enforces staff/admin requirements.
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the return-pass service, its repository and the authorization service. */
    public ReturnPassController(ReturnPassService returnPassService, ReturnPassRepository returnPassRepository, DemoAuthorizationService authorizationService) {
        this.returnPassService = returnPassService;
        this.returnPassRepository = returnPassRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * POST {@code /api/claims/{claimId}/return-pass} — issue a return pass for an approved claim.
     *
     * @param claimId path variable: the claim the pass is issued against.
     * @param request request body: pass creation details ({@link ReturnPassRequest}).
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the created {@link ReturnPassResponse}.
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin (the resolved admin
     *         {@link AppUser} is recorded as the issuer).
     */
    @PostMapping("/api/claims/{claimId}/return-pass")
    public ReturnPassResponse create(
            @PathVariable String claimId,
            @RequestBody ReturnPassRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Only staff/admin may issue a pass; the returned user is attributed as the issuer.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.create(claimId, request, admin);
    }

    /**
     * GET {@code /api/return-passes/{idOrClaimId}} — fetch a return pass by either its own id or a claim id.
     *
     * <p>The path segment is overloaded: it is first treated as a pass id, and if no pass has that
     * id, it is treated as a claim id (returning that claim's first pass). The service performs the
     * per-caller access check, so this endpoint does not pre-require staff/admin here.
     *
     * @param idOrClaimId path variable: a return-pass id, or failing that a claim id.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller (passed to
     *        the service for access control).
     * @return 200 OK with the resolved {@link ReturnPassResponse}.
     * @throws NotFoundException 404 when neither a pass with that id nor a pass for that claim exists.
     */
    @GetMapping("/api/return-passes/{idOrClaimId}")
    public ReturnPassResponse get(@PathVariable String idOrClaimId, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Try by pass ID first, then by claimId
        if (returnPassRepository.existsById(idOrClaimId)) {
            return returnPassService.get(idOrClaimId, userEmail, authorizationService);
        }
        // Fall back to interpreting the segment as a claim id; use that claim's first pass.
        List<ReturnPass> byClaimId = returnPassRepository.findByClaimId(idOrClaimId);
        if (!byClaimId.isEmpty()) {
            String passId = byClaimId.get(0).getId();
            return returnPassService.get(passId, userEmail, authorizationService);
        }
        // Neither interpretation matched — nothing to return.
        throw new NotFoundException("Return Pass not found");
    }

    /**
     * POST {@code /api/return-passes/verify} — validate a pass code without redeeming it.
     *
     * <p>No authorization header is required (used in the claimant/staff verification step).
     *
     * @param request request body: the code to verify ({@link ReturnPassVerifyRequest});
     *        {@link Valid} triggers bean validation (400 on constraint violations).
     * @return 200 OK with a {@link ReturnPassVerifyResponse} indicating validity/details.
     */
    @PostMapping("/api/return-passes/verify")
    public ReturnPassVerifyResponse verify(@Valid @RequestBody ReturnPassVerifyRequest request) {
        return returnPassService.verify(request);
    }

    /**
     * POST {@code /api/return-passes/redeem} — redeem a pass identified by its code (no path id).
     *
     * @param request request body: the redemption details including the code ({@link ReturnPassRedeemRequest}).
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link ReturnPassResponse} (now redeemed).
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin (recorded as redeemer).
     */
    @PostMapping("/api/return-passes/redeem")
    public ReturnPassResponse redeemByCode(
            @RequestBody ReturnPassRedeemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Redemption is a staff/admin counter action; the resolved admin is recorded as redeemer.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.redeemByCode(request, admin);
    }

    /**
     * POST {@code /api/return-passes/{id}/redeem} — redeem a specific pass by its id.
     *
     * @param id path variable: the return-pass id to redeem.
     * @param request request body: redemption details ({@link ReturnPassRedeemRequest});
     *        {@link Valid} triggers bean validation (400 on constraint violations).
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link ReturnPassResponse} (now redeemed).
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin (recorded as redeemer).
     */
    @PostMapping("/api/return-passes/{id}/redeem")
    public ReturnPassResponse redeem(
            @PathVariable String id,
            @Valid @RequestBody ReturnPassRedeemRequest request,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Staff/admin only; the resolved admin is attributed as the redeemer.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.redeem(id, request, admin);
    }

    /**
     * POST {@code /api/return-passes/{id}/reminder} — send a pickup reminder for an unredeemed pass.
     *
     * @param id path variable: the return-pass id to remind about.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the {@link ReturnPassResponse} (reflecting the reminder action).
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin (recorded as sender).
     */
    @PostMapping("/api/return-passes/{id}/reminder")
    public ReturnPassResponse sendPickupReminder(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Sending a reminder is a staff/admin action; the resolved admin is recorded as the sender.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return returnPassService.sendPickupReminder(id, admin);
    }
}
