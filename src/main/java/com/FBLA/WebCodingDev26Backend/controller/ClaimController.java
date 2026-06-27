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

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimRepository claims;
    private final CaseMessageRepository caseMessages;
    private final DemoAuthorizationService authorizationService;
    private final FoundItemRepository foundItems;
    private final RecoveryCaseService recoveryCaseService;
    private final CompletionCleanupService completionCleanup;

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
    ClaimController(ClaimRepository claims, DemoAuthorizationService authorizationService) {
        this(claims, null, authorizationService, null, null, null);
    }

    @GetMapping("/mine")
    public List<Claim> mine(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to view claims.");
        }
        return claims.findByClaimantEmail(verifiedEmail);
    }

    @PostMapping("/{id}/cancel")
    public Claim cancel(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("You can only withdraw your own claim.");
        }
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase();
        if (List.of("approved", "completed", "rejected", "cancelled").contains(status)) {
            throw new BadRequestException("This claim can no longer be withdrawn.");
        }
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
    @PostMapping("/{id}/confirm-receipt")
    public Claim confirmReceipt(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("You can only confirm your own claim.");
        }
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase();
        boolean alreadyConfirmed = "completed".equals(status)
                || (claim.getReceivedConfirmedAt() != null && !claim.getReceivedConfirmedAt().isBlank());
        if (alreadyConfirmed) {
            return claim;
        }
        if (!"approved".equals(status)) {
            throw new BadRequestException("Only an approved claim can be confirmed as received.");
        }

        String now = Instant.now().toString();
        claim.setStatus("completed");
        claim.setReceivedConfirmedAt(now);
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);

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

    @GetMapping("/{id}")
    public Claim get(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (authorizationService.isStaffOrAdmin(userEmail)) {
            return claim;
        }
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank() || !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        return claim;
    }

    @GetMapping("/{id}/case-messages")
    public List<CaseMessage> getCaseMessages(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        if (caseMessages == null) {
            return List.of();
        }
        List<CaseMessage> messages = caseMessages.findByClaimIdOrderByCreatedAtAsc(id);
        // Auto-mark unread messages as read for the caller
        String now = Instant.now().toString();
        boolean anyUpdated = false;
        for (CaseMessage msg : messages) {
            // Mark messages from other senders as read
            if (!Boolean.TRUE.equals(msg.getIsRead()) && !verifiedEmail.equalsIgnoreCase(msg.getSenderId())) {
                msg.setIsRead(true);
                msg.setReadAt(now);
                anyUpdated = true;
            }
        }
        if (anyUpdated) {
            caseMessages.saveAll(messages);
        }
        return messages;
    }

    @GetMapping("/{id}/case-messages/unread-count")
    public Map<String, Object> getUnreadCount(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        if (caseMessages == null) {
            return Map.of("count", 0);
        }
        long count = caseMessages.findByClaimIdOrderByCreatedAtAsc(id).stream()
                .filter(msg -> !Boolean.TRUE.equals(msg.getIsRead()) && !verifiedEmail.equalsIgnoreCase(msg.getSenderId()))
                .count();
        return Map.of("count", count);
    }

    @PostMapping("/{id}/case-messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseMessage postCaseMessage(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        AppUser user = authorizationService.currentUser(userEmail);
        String senderRole = user != null ? user.getRole() : "student";
        if (!authorizationService.isStaffOrAdmin(userEmail) && !verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Access denied.");
        }
        String message = data.get("message") == null ? "" : String.valueOf(data.get("message")).trim();
        if (message.isBlank()) {
            throw new BadRequestException("Message is required.");
        }
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

        if (caseMessages == null) {
            return msg;
        }
        return caseMessages.save(msg);
    }

    @PostMapping("/{id}/rating")
    public Claim submitRating(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to submit a rating.");
        }
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!verifiedEmail.equalsIgnoreCase(claim.getClaimantEmail())) {
            throw new ForbiddenException("Only the claimant can submit a rating.");
        }
        Object ratingVal = data.get("rating");
        if (ratingVal == null) {
            throw new BadRequestException("Rating is required.");
        }
        int ratingInt;
        try {
            ratingInt = Integer.parseInt(String.valueOf(ratingVal));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Rating must be an integer.");
        }
        if (ratingInt < 1 || ratingInt > 5) {
            throw new BadRequestException("Rating must be between 1 and 5.");
        }
        String review = data.get("review") != null ? String.valueOf(data.get("review")).trim() : null;

        String now = Instant.now().toString();
        claim.setClaimantRating(ratingInt);
        claim.setClaimantReview(review);
        // Submitted reviews enter the staff moderation queue as "pending".
        claim.setReviewStatus("pending");
        claim.setReviewSubmittedAt(now);
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);

        // Also add to FoundItem's ratings array
        if (foundItems != null && claim.getFoundItemId() != null) {
            foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
                Rating rating = new Rating();
                rating.setClaimId(id);
                rating.setRating(ratingInt);
                rating.setReview(review);
                rating.setClaimantName(claim.getClaimantName());
                rating.setReviewerEmail(verifiedEmail);
                rating.setReviewSubmittedAt(Instant.now().toString());
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
