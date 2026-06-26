package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
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

    @Autowired
    public ClaimController(ClaimRepository claims, CaseMessageRepository caseMessages, DemoAuthorizationService authorizationService, FoundItemRepository foundItems) {
        this.claims = claims;
        this.caseMessages = caseMessages;
        this.authorizationService = authorizationService;
        this.foundItems = foundItems;
    }

    // Package-private constructor for test compatibility
    ClaimController(ClaimRepository claims, DemoAuthorizationService authorizationService) {
        this(claims, null, authorizationService, null);
    }

    @GetMapping("/mine")
    public List<Claim> mine(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        String verifiedEmail = authorizationService.resolveEmail(userEmail);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new ForbiddenException("Sign in is required to view claims.");
        }
        return claims.findByClaimantEmail(verifiedEmail);
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
                AppUser user = authorizationService.currentUser(userEmail);
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
