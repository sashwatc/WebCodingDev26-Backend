package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.service.CompletionCleanupService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.RecoveryCaseService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claimant-facing controller for claims on found items.
 *
 * <p>Serves the base route {@code /api/claims}. This is the STUDENT side of the claim
 * lifecycle (the staff/admin side lives in {@code AdminDashboardController}). It lets a
 * signed-in claimant list their own claims, withdraw a claim, confirm receipt of a
 * returned item, read a single claim, exchange case messages with staff, and rate a
 * completed recovery.</p>
 *
 * <p>Identity is always resolved from the {@code X-Demo-User-Email} header via
 * {@link DemoAuthorizationService#resolveEmail} (a verified email; never trusted from
 * the client directly). Most endpoints require sign-in, and ownership is enforced by
 * comparing the verified email against the claim's {@code claimantEmail}; staff/admins
 * may additionally read any claim's details/messages.</p>
 *
 * <p>Collaborators: {@link ClaimRepository} (claims), {@link CaseMessageRepository}
 * (claim ↔ staff messaging), {@link FoundItemRepository} (item status & ratings),
 * {@link RecoveryCaseService} (advances the recovery case on return), and
 * {@link CompletionCleanupService} (purges fully-completed items). Several collaborators
 * may be null under the test-only constructor and are therefore null-guarded.</p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/claims") // base route for all claimant-facing claim endpoints
public class ClaimController {
    // Claim persistence and lookup (by id and by claimant email).
    private final ClaimRepository claims;
    // Persistence of case messages exchanged between claimant and staff on a claim.
    private final CaseMessageRepository caseMessages;
    // Resolves the caller's verified email/role and provides staff/admin checks.
    private final DemoAuthorizationService authorizationService;
    // Found-item persistence — updated on receipt confirmation (archive) and rating submission.
    private final FoundItemRepository foundItems;
    // Advances the recovery-case state machine when an item is confirmed returned.
    private final RecoveryCaseService recoveryCaseService;
    // Cascade-deletes a found item and everything referencing it once it is fully completed.
    private final CompletionCleanupService completionCleanup;

    /** Primary (Spring-injected) constructor wiring all collaborators. */
    @Autowired
    public ClaimController(
            ClaimRepository claims,
            CaseMessageRepository caseMessages,
            DemoAuthorizationService authorizationService,
            FoundItemRepository foundItems,
            RecoveryCaseService recoveryCaseService,
            CompletionCleanupService completionCleanup
    ) {
        this.claims = claims;
        this.caseMessages = caseMessages;
        this.authorizationService = authorizationService;
        this.foundItems = foundItems;
        this.recoveryCaseService = recoveryCaseService;
        this.completionCleanup = completionCleanup;
    }

    // Package-private constructor for test compatibility
    // Wires only the claim repo and authorization service; all other collaborators are null
    // (the endpoints null-guard these, degrading gracefully when they are absent).
    ClaimController(ClaimRepository claims, DemoAuthorizationService authorizationService) {
        this(claims, null, authorizationService, null, null, null);
    }

    /**
     * GET /api/claims/mine — list the signed-in user's own claims.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the caller's {@link Claim} records (matched by verified claimant email); 200 OK
     * Authorization: sign-in required. Errors: {@link ForbiddenException} if the caller cannot be
     * resolved to a verified email (not signed in).
     */
    @GetMapping("/mine")
    public List<Claim> mine(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to view claims.");
        }
        // Return only claims belonging to this caller.
        return claims.findByClaimantEmail(verifiedEmail);
    }

    /**
     * POST /api/claims/{id}/cancel — withdraw (cancel) the caller's own claim.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the updated {@link Claim} with status "cancelled"; 200 OK
     * Authorization: sign-in required and the caller must own the claim.
     * Errors: {@link ForbiddenException} if not signed in or not the owner; {@link NotFoundException}
     * if the claim does not exist; {@link BadRequestException} if the claim is already
     * approved/completed/rejected/cancelled (no longer withdrawable).
     */
    @PostMapping("/{id}/cancel")
    public Claim cancel(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Ownership check: only the claimant may withdraw their claim.
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("You can only withdraw your own claim.");
        }
        // Normalize status for the state-guard below.
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase();
        // Terminal/decided states cannot be withdrawn.
        if (List.of("approved", "completed", "rejected", "cancelled").contains(status)) {
            throw new BadRequestException("This claim can no longer be withdrawn.");
        }
        // Transition to cancelled and stamp the update time.
        claim.setStatus("cancelled");
        claim.setUpdatedDate(Instant.now().toString());
        return claims.save(claim);
    }

    /**
     * Claimant confirms they have physically received the item back. This is the
     * student-side completion trigger: it marks the approved claim "completed",
     * archives the found item, advances the recovery case, and (because the claim
     * is now approved AND completed) cascade-deletes the item and everything that
     * references it. Idempotent: a claim already confirmed is returned unchanged.
     */
    /**
     * POST /api/claims/{id}/confirm-receipt — claimant confirms physical receipt of the item.
     *
     * <p>See the detailed note above: marks the approved claim "completed", archives the
     * found item, advances the recovery case, then purges the completed item and its
     * references. Idempotent.</p>
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the updated/confirmed {@link Claim}; 200 OK. If already confirmed, the existing claim is
     *         returned unchanged (idempotent).
     * Authorization: sign-in required and the caller must own the claim.
     * Errors: {@link ForbiddenException} if not signed in or not the owner; {@link NotFoundException}
     * if the claim does not exist; {@link BadRequestException} if the claim is not in "approved" status.
     */
    @PostMapping("/{id}/confirm-receipt")
    public Claim confirmReceipt(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Ownership check: only the claimant may confirm receipt.
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("You can only confirm your own claim.");
        }
        // Normalize status for the idempotency / precondition checks.
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase();
        // Idempotency: already completed, or a confirmation timestamp already set.
        boolean alreadyConfirmed = "completed".equals(status)
                || (claim.getReceivedConfirmedAt() != null && !claim.getReceivedConfirmedAt().isBlank());
        if (alreadyConfirmed) {
            return claim;
        }
        // Precondition: receipt can only be confirmed for an approved claim.
        if (!"approved".equals(status)) {
            throw new BadRequestException("Only an approved claim can be confirmed as received.");
        }

        // Single timestamp reused across the claim and item updates below.
        String now = Instant.now().toString();
        // Transition the claim to completed and record the receipt time.
        claim.setStatus("completed");
        claim.setReceivedConfirmedAt(now);
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);

        // Archive the underlying found item and flag it as claim-confirmed (null-guarded for tests).
        String foundItemId = claim.getFoundItemId();
        if (foundItems != null && foundItemId != null && !foundItemId.isBlank()) {
            foundItems.findById(foundItemId).ifPresent(item -> {
                item.setStatus(ItemStatus.ARCHIVED);
                item.setClaimConfirmed(true);
                item.setClaimConfirmedAt(now);
                item.setUpdatedDate(now);
                foundItems.save(item);
            });
        }
        // Advance the recovery case to "returned" — best-effort, must not block confirmation.
        if (recoveryCaseService != null) {
            try {
                recoveryCaseService.markReturned(claim.getId(), foundItemId);
            } catch (RuntimeException ignored) {
                // Recovery-case advancement is best-effort; never block the confirmation.
            }
        }
        // Approved + completed: the item's lifecycle is finished — purge it and
        // every record that references it so nothing orphaned remains.
        if (completionCleanup != null) {
            try {
                completionCleanup.purgeCompletedItem(foundItemId);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup; the claim is already confirmed completed.
            }
        }
        return saved;
    }

    /**
     * GET /api/claims/{id} — fetch a single claim.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the {@link Claim}; 200 OK
     * Authorization: staff/admins may read any claim; otherwise the caller must be the claim's owner.
     * Errors: {@link NotFoundException} if the claim does not exist; {@link ForbiddenException} if the
     * caller is neither staff/admin nor the owner.
     */
    @GetMapping("/{id}")
    public Claim get(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Staff/admins may view any claim.
        if (authorizationService.isStaffOrAdmin(userEmail)) {
            return claim;
        }
        // Otherwise the caller must be signed in AND be the claim's owner.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank() || !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        return claim;
    }

    /**
     * GET /api/claims/{id}/case-messages — list the case-message thread for a claim.
     *
     * <p>Side effect: messages sent by the OTHER party that are still unread are auto-marked read
     * (with a read timestamp) for the caller.</p>
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the messages ordered oldest-first; 200 OK (empty list if messaging is unavailable).
     * Authorization: sign-in required; caller must be staff/admin OR the claim owner.
     * Errors: {@link ForbiddenException} if not signed in or not authorized; {@link NotFoundException}
     * if the claim does not exist.
     */
    @GetMapping("/{id}/case-messages")
    public List<CaseMessage> getCaseMessages(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Access: staff/admin OR the claim owner only.
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        // Messaging may be unavailable (test constructor) — return an empty thread.
        if (caseMessages == null) {
            return List.of();
        }
        // Fetch the thread oldest-first.
        List<CaseMessage> messages = caseMessages.findByClaimIdOrderByCreatedAtAsc(id);
        // Auto-mark unread messages as read for the caller
        String now = Instant.now().toString();
        boolean anyUpdated = false; // track whether any message changed so we only persist when needed
        for (CaseMessage msg : messages) {
            // Mark messages from other senders as read
            // (don't mark the caller's own messages; only inbound unread ones).
            if (!Boolean.TRUE.equals(msg.getIsRead()) && !verifiedEmail.equalsIgnoreCase(msg.getSenderId())) {
                msg.setIsRead(true);
                msg.setReadAt(now);
                anyUpdated = true;
            }
        }
        // Persist read-state changes in a single batch only if something changed.
        if (anyUpdated) {
            caseMessages.saveAll(messages);
        }
        return messages;
    }

    /**
     * GET /api/claims/{id}/case-messages/unread-count — count messages unread by the caller.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return a map {@code {"count": n}} of inbound messages (from the other party) still unread; 200 OK.
     *         Unlike the list endpoint, this does NOT mark anything as read.
     * Authorization: sign-in required; caller must be staff/admin OR the claim owner.
     * Errors: {@link ForbiddenException} if not signed in or not authorized; {@link NotFoundException}
     * if the claim does not exist.
     */
    @GetMapping("/{id}/case-messages/unread-count")
    public Map<String, Object> getUnreadCount(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Access: staff/admin OR the claim owner only.
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        // Messaging unavailable → zero.
        if (caseMessages == null) {
            return Map.of("count", 0);
        }
        // Count inbound (not authored by caller) messages that are still unread.
        long count = caseMessages.findByClaimIdOrderByCreatedAtAsc(id).stream()
                .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()) && !verifiedEmail.equalsIgnoreCase(msg.getSenderId()))
                .count();
        return Map.of("count", count);
    }

    /**
     * POST /api/claims/{id}/case-messages — post a new message to the claim's case thread.
     *
     * @param id        path variable: the claim's id
     * @param data      request body containing a required {@code "message"} string field
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the created {@link CaseMessage}. Status: 201 CREATED (via {@code @ResponseStatus}).
     * Authorization: sign-in required; caller must be staff/admin OR the claim owner.
     * Side effect: if a NON-staff caller (the claimant) replies while the claim is in "need_more_info",
     * the claim is moved back to "under_review" so staff re-evaluate the added info.
     * Errors: {@link ForbiddenException} if not signed in or not authorized; {@link NotFoundException}
     * if the claim does not exist; {@link BadRequestException} if {@code message} is blank.
     */
    @PostMapping("/{id}/case-messages")
    @ResponseStatus(HttpStatus.CREATED) // return 201 Created on success
    public CaseMessage postCaseMessage(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Resolve the sender's role for stamping on the message (default "student" if unknown).
        AppUser user = authorizationService.currentUser(userEmail);
        String senderRole = user != null ? user.getRole() : "student";
        // Access: staff/admin OR the claim owner only.
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        // Message text is required.
        String message = data.get("message") == null ? "" : String.valueOf(data.get("message")).trim();
        if (message.isBlank()) {
            throw new BadRequestException("Message is required.");
        }
        // Build the new message with a generated short id and the sender's verified email/role.
        CaseMessage msg = new CaseMessage();
        msg.setId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        msg.setClaimId(id);
        msg.setSenderId(verifiedEmail);
        msg.setSenderRole(senderRole);
        msg.setMessage(message);
        msg.setCreatedAt(Instant.now().toString());

        // When the claimant replies to a "need_more_info" claim, move it back into
        // the staff review queue so the additional information gets re-evaluated.
        if (!authorizationService.isStaffOrAdmin(userEmail)
                && claim.getStatus() != null
                && "need_more_info".equalsIgnoreCase(claim.getStatus().trim())) {
            claim.setStatus("under_review");
            claim.setUpdatedDate(Instant.now().toString());
            claims.save(claim);
        }

        // Messaging unavailable (test constructor) → return the in-memory message without persisting.
        if (caseMessages == null) {
            return msg;
        }
        return caseMessages.save(msg);
    }

    /**
     * POST /api/claims/{id}/rating — submit a claimant rating/review for a recovery.
     *
     * @param id        path variable: the claim's id
     * @param data      request body: required integer {@code "rating"} (1–5) and optional {@code "review"} text
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header
     * @return the updated {@link Claim} carrying the rating, review, "pending" review status, and timestamps; 200 OK.
     * Authorization: sign-in required and ONLY the claimant (claim owner) may rate.
     * Side effect: the rating is also appended to the associated {@link FoundItem}'s ratings list
     * (replacing any prior rating from the same claim).
     * Errors: {@link ForbiddenException} if not signed in or not the claimant; {@link NotFoundException}
     * if the claim does not exist; {@link BadRequestException} if rating is missing, non-integer, or outside 1–5.
     */
    @PostMapping("/{id}/rating")
    public Claim submitRating(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Resolve and require a verified caller email.
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to submit a rating.");
        }
        // Load the claim or 404.
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        // Only the claimant may rate their own recovery.
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Only the claimant can submit a rating.");
        }
        // Rating value is required.
        Object ratingVal = data.get("rating");
        if (ratingVal == null) {
            throw new BadRequestException("Rating is required.");
        }
        // Parse the rating as an integer.
        int ratingInt;
        try {
            ratingInt = Integer.parseInt(String.valueOf(ratingVal));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Rating must be an integer.");
        }
        // Enforce the 1–5 range.
        if (ratingInt < 1 || ratingInt > 5) {
            throw new BadRequestException("Rating must be between 1 and 5.");
        }
        // Optional free-text review (null when omitted).
        String review = data.get("review") != null ? String.valueOf(data.get("review")).trim() : null;

        // Stamp the rating onto the claim; reviews start "pending" for staff moderation.
        String now = Instant.now().toString();
        claim.setClaimantRating(ratingInt);
        claim.setClaimantReview(review);
        // Submitted reviews enter the staff moderation queue as "pending".
        claim.setReviewStatus("pending");
        claim.setReviewSubmittedAt(now);
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);

        // Also add to FoundItem's ratings array
        // (null-guarded for tests / claims with no linked item).
        if (foundItems != null && claim.getFoundItemId() != null) {
            foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
                // Build the rating entry mirroring the claim's rating/review and reviewer identity.
                Rating rating = new Rating();
                rating.setClaimId(id);
                rating.setRating(ratingInt);
                rating.setReview(review);
                rating.setClaimantName(claim.getClaimantName());
                rating.setReviewerEmail(verifiedEmail);
                rating.setReviewSubmittedAt(Instant.now().toString());
                // Copy the existing ratings (or start fresh), drop any prior rating from this same claim,
                // then add the new one — keeping one rating per claim (idempotent re-rating).
                List<Rating> ratings = item.getRatings() == null ? new ArrayList<>() : new ArrayList<>(item.getRatings());
                ratings.removeIf(r -> id.equals(r.getClaimId()));
                ratings.add(rating);
                item.setRatings(ratings);
                item.setUpdatedDate(Instant.now().toString());
                foundItems.save(item);
            });
        }
        return saved;
    }
}
