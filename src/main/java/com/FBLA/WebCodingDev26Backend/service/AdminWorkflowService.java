package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Owns the staff/admin side of the lost-and-found workflow: reviewing and
 * resolving ownership claims against found items, surfacing the admin dashboard,
 * and recording an audit trail for every privileged action.
 *
 * <p>Business logic this service owns:
 * <ul>
 *   <li>Claim lifecycle transitions — approve, deny, request-more-info, and
 *       complete (hand-off) — and the corresponding {@link ItemStatus}
 *       transitions on the associated found item.</li>
 *   <li>Deciding when an item should return to the open {@code FOUND} pool (only
 *       when no other claim is still pending) versus stay verified/archived.</li>
 *   <li>Emitting email notifications and writing an {@link AuditLog} entry for
 *       each privileged action.</li>
 *   <li>Triggering cascade cleanup of an item once its claim is completed.</li>
 * </ul>
 *
 * <p>Collaborators: the found-item, lost-report, claim, case-message, audit-log
 * and notification repositories (persistence); {@link EmailNotificationService}
 * (claimant emails); {@link ClockService} (timestamps); {@link InputSanitizer}
 * (cleaning admin-supplied note text); and {@link CompletionCleanupService}
 * (cascade-deleting a fully resolved item and its references).
 */
@Service
public class AdminWorkflowService {
    /** Found-item persistence: lookups and status updates as claims are resolved. */
    private final FoundItemRepository foundItems;
    /** Lost-report persistence; exposed read-only to the dashboard and listing endpoints. */
    private final LostReportRepository lostReports;
    /** Claim persistence: the primary entity whose lifecycle this service drives. */
    private final ClaimRepository claims;
    /** Case-message thread persistence; used to post "more info requested" notes back to claimants. */
    private final CaseMessageRepository caseMessages;
    /** Audit-log persistence: every privileged admin action is recorded here. */
    private final AuditLogRepository auditLogs;
    /** Notification persistence; exposed read-only to the dashboard and listing endpoints. */
    private final NotificationRepository notifications;
    /** Sends claimant-facing emails on claim approval/denial. */
    private final EmailNotificationService emailNotifications;
    /** Supplies ISO timestamps for all status-transition and audit writes. */
    private final ClockService clock;
    /** Cleans/escapes admin-supplied free-text notes before they are persisted. */
    private final InputSanitizer sanitizer;
    /** Cascade-deletes a found item and everything referencing it once its claim completes. */
    private final CompletionCleanupService completionCleanup;

    /**
     * Constructs the service with all repository and collaborator dependencies
     * injected by Spring. No side effects beyond field assignment.
     */
    public AdminWorkflowService(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            CaseMessageRepository caseMessages,
            AuditLogRepository auditLogs,
            NotificationRepository notifications,
            EmailNotificationService emailNotifications,
            ClockService clock,
            InputSanitizer sanitizer,
            CompletionCleanupService completionCleanup
    ) {
        this.foundItems = foundItems;
        this.lostReports = lostReports;
        this.claims = claims;
        this.caseMessages = caseMessages;
        this.auditLogs = auditLogs;
        this.notifications = notifications;
        this.emailNotifications = emailNotifications;
        this.clock = clock;
        this.sanitizer = sanitizer;
        this.completionCleanup = completionCleanup;
    }

    /**
     * Builds the admin dashboard snapshot: all found items, lost reports, claims,
     * the subset of claims still awaiting review, all notifications, and the 25
     * most recent audit-log entries.
     *
     * @return an immutable map keyed by dashboard section name. Read-only; no DB writes.
     */
    public Map<String, Object> dashboard() {
        List<Claim> allClaims = claims.findAll();
        // Most-recent-first audit feed: sort by createdDate descending (nulls last),
        // then keep only the latest 25 entries for the dashboard.
        List<AuditLog> recentAudit = auditLogs.findAll().stream()
                .sorted(Comparator.comparing(log -> log.getCreatedDate(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(25)
                .toList();

        return Map.of(
                "found_items", foundItems.findAll(),
                "lost_reports", lostReports.findAll(),
                "claims", allClaims,
                "pending_claims", allClaims.stream().filter(this::isPendingClaim).toList(),
                "notifications", notifications.findAll(),
                "audit_logs", recentAudit
        );
    }

    /** @return every lost report; read-only listing for staff views. */
    public List<?> listLostReports() {
        return lostReports.findAll();
    }

    /** @return every claim; read-only listing for staff views. */
    public List<Claim> listClaims() {
        return claims.findAll();
    }

    /** @return every audit-log entry; read-only listing for staff views. */
    public List<AuditLog> listAuditLogs() {
        return auditLogs.findAll();
    }

    /** @return every notification; read-only listing for staff views. */
    public List<Notification> listNotifications() {
        return notifications.findAll();
    }

    /**
     * Approves a claim and verifies the associated found item as belonging to the claimant.
     *
     * <p>Side effects / business rules:
     * <ul>
     *   <li>Sets the claim status to {@code approved}, stamps reviewer email and time,
     *       and stores the admin note (sanitized, or a default).</li>
     *   <li>Transitions the found item to {@link ItemStatus#VERIFIED} and marks
     *       {@code claimConfirmed} true with a confirmation timestamp.</li>
     *   <li>Sends the claimant an "approved" email.</li>
     *   <li>Writes a {@code CLAIM_APPROVED} audit entry.</li>
     * </ul>
     * Note: the return-pass / claim code is issued separately (deliberate admin step),
     * not as an automatic side effect of approval.
     *
     * @param claimId id of the claim to approve
     * @param admin   the acting staff/admin user (recorded as reviewer)
     * @param data    optional payload carrying an admin note/message
     * @return the saved, approved claim
     * @throws NotFoundException if the claim or its found item does not exist
     */
    public Claim approveClaim(String claimId, AppUser admin, Map<String, Object> data) {
        // Load the claim and its found item up front; both must exist.
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        String now = clock.now();

        // Mark the claim approved and record who reviewed it and when.
        claim.setStatus("approved");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Claim approved by admin."));
        claim.setUpdatedDate(now);
        Claim savedClaim = claims.save(claim);

        // Item is now confirmed to belong to the claimant: move it to VERIFIED.
        item.setStatus(ItemStatus.VERIFIED);
        item.setClaimConfirmed(true);
        item.setClaimConfirmedAt(now);
        item.setUpdatedDate(now);
        foundItems.save(item);

        // Notify the claimant by email that their claim was approved.
        emailNotifications.sendClaimApproved(savedClaim, item);

        // Note: the claim code (return pass) is issued as an explicit, separate
        // admin step after approval — see ReturnPassService.create — so issuance
        // remains a deliberate action rather than an automatic side effect.

        // Record the approval in the audit trail (machine detail + human-readable line).
        audit("CLAIM_APPROVED", "Claim", savedClaim.getId(), admin.getEmail(),
                "Approved claim for found item " + item.getId() + ".",
                admin.getRole() + " approved " + savedClaim.getClaimantName() + "'s claim for " + item.getTitle());
        return savedClaim;
    }

    /**
     * Staff completes the hand-off for an approved claim. Mirrors the student
     * "confirm receipt" and the Return Pass redeem: marks the claim completed,
     * archives the item, and — because the claim is now approved AND completed —
     * cascade-deletes the item and everything referencing it so no path can leave
     * an approved+completed item lingering in the dashboard.
     *
     * @param claimId id of the (already approved) claim being handed off
     * @param admin   the acting staff/admin user
     * @param data    optional payload carrying an admin note/message
     * @return the saved, completed claim
     * @throws NotFoundException if the claim does not exist
     */
    public Claim completeClaim(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        String now = clock.now();

        // Mark the claim completed; only set the received-confirmation time if not already stamped.
        claim.setStatus("completed");
        if (claim.getReceivedConfirmedAt() == null || claim.getReceivedConfirmedAt().isBlank()) {
            claim.setReceivedConfirmedAt(now);
        }
        claim.setAdminNotes(noteFrom(data, "Hand-off completed by staff."));
        claim.setUpdatedDate(now);
        Claim savedClaim = claims.save(claim);

        // Archive the underlying found item (if still present) and confirm the claim on it.
        String foundItemId = claim.getFoundItemId();
        if (foundItemId != null) {
            foundItems.findById(foundItemId).ifPresent(item -> {
                item.setStatus(ItemStatus.ARCHIVED);
                item.setClaimConfirmed(true);
                item.setClaimConfirmedAt(now);
                item.setUpdatedDate(now);
                foundItems.save(item);
            });
        }

        // Audit the completed hand-off.
        audit("CLAIM_COMPLETED", "Claim", savedClaim.getId(), admin.getEmail(),
                "Completed hand-off for found item " + foundItemId + ".",
                admin.getRole() + " completed " + savedClaim.getClaimantName() + "'s claim.");

        // Cascade-delete the item and all references now that it is fully resolved.
        // Best-effort: any failure is swallowed because the claim is already completed.
        if (completionCleanup != null) {
            try {
                completionCleanup.purgeCompletedItem(foundItemId);
            } catch (RuntimeException ignored) {
                // Best-effort cascade; the claim is already marked completed.
            }
        }
        return savedClaim;
    }

    /**
     * Denies a claim and, when no other claim is still pending, returns the item to the open pool.
     *
     * <p>Side effects / business rules:
     * <ul>
     *   <li>Sets claim status to {@code rejected}, stamps reviewer and time, stores the note.</li>
     *   <li>If no <em>other</em> pending claim exists for the same item, resets the item to
     *       {@link ItemStatus#FOUND} and clears {@code claimConfirmed} so it can be claimed again;
     *       otherwise the item is left untouched.</li>
     *   <li>Sends the claimant a "denied" email and writes a {@code CLAIM_DENIED} audit entry.</li>
     * </ul>
     *
     * @param claimId id of the claim to deny
     * @param admin   the acting staff/admin user
     * @param data    optional payload carrying an admin note/message
     * @return the saved, rejected claim
     * @throws NotFoundException if the claim or its found item does not exist
     */
    public Claim denyClaim(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        String now = clock.now();

        // Mark the claim rejected and record reviewer details.
        claim.setStatus("rejected");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Claim denied by admin."));
        claim.setUpdatedDate(now);
        Claim savedClaim = claims.save(claim);

        // Only release the item back to the open pool if no OTHER claim for it is still pending.
        boolean hasOtherActiveClaim = claims.findByFoundItemId(item.getId()).stream()
                .anyMatch(existing -> !existing.getId().equals(savedClaim.getId()) && isPendingClaim(existing));
        if (!hasOtherActiveClaim) {
            // No competing claims: re-open the item so others may claim it.
            item.setStatus(ItemStatus.FOUND);
            item.setClaimConfirmed(false);
            item.setUpdatedDate(now);
            foundItems.save(item);
        }

        // Notify the claimant of the denial and audit the action.
        emailNotifications.sendClaimDenied(savedClaim, item);
        audit("CLAIM_DENIED", "Claim", savedClaim.getId(), admin.getEmail(),
                "Denied claim for found item " + item.getId() + ".",
                admin.getRole() + " denied " + savedClaim.getClaimantName() + "'s claim for " + item.getTitle());
        return savedClaim;
    }

    /**
     * Archives a found item directly (admin housekeeping for resolved items).
     *
     * @param itemId id of the found item to archive
     * @param admin  the acting staff/admin user
     * @param data   optional payload carrying an admin note/message
     * @return the saved, archived item
     * @throws NotFoundException if the item does not exist
     *         Side effect: writes an {@code ITEM_ARCHIVED} audit entry.
     */
    public FoundItem archiveItem(String itemId, AppUser admin, Map<String, Object> data) {
        FoundItem item = foundItems.findById(itemId).orElseThrow(() -> new NotFoundException("Found item not found"));
        item.setStatus(ItemStatus.ARCHIVED);
        item.setUpdatedDate(clock.now());
        FoundItem saved = foundItems.save(item);
        audit("ITEM_ARCHIVED", "FoundItem", saved.getId(), admin.getEmail(), noteFrom(data, "Archived resolved found item."),
                admin.getRole() + " archived item " + saved.getTitle());
        return saved;
    }

    /**
     * Determines whether a claim still counts as "pending" (awaiting staff action).
     * A claim is pending when its status is blank/unset or one of the open review
     * states: {@code submitted}, {@code pending_review}, {@code under_review},
     * {@code need_more_info}. Comparison is case-insensitive and trimmed.
     */
    private boolean isPendingClaim(Claim claim) {
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase(Locale.ROOT);
        return status.isBlank() || List.of("submitted", "pending_review", "under_review", "need_more_info").contains(status);
    }

    /**
     * Moves a claim into the {@code need_more_info} state and posts the request into
     * the claim's case-message thread so the claimant can see and respond to it.
     *
     * <p>Side effects: saves the claim, persists a new staff {@link CaseMessage}, and
     * writes a {@code CLAIM_MORE_INFO} audit entry.
     *
     * @param claimId id of the claim
     * @param admin   the acting staff/admin user (recorded as message sender and reviewer)
     * @param data    optional payload carrying the message to the claimant
     * @return the saved claim
     * @throws NotFoundException if the claim does not exist
     */
    public Claim requestMoreInfo(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        String now = clock.now();
        // Transition to the "need more info" review state and record reviewer details.
        claim.setStatus("need_more_info");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Admin requested additional information."));
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);
        // Surface the request in the claim's case-message thread so the claimant sees it.
        CaseMessage note = new CaseMessage();
        // Generate a compact unique id (10 hex chars from a UUID with dashes removed).
        note.setId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        note.setClaimId(saved.getId());
        note.setSenderId(admin.getEmail());
        note.setSenderRole("staff");
        note.setMessage(saved.getAdminNotes());
        note.setCreatedAt(now);
        caseMessages.save(note);
        audit("CLAIM_MORE_INFO", "Claim", saved.getId(), admin.getEmail(), saved.getAdminNotes(),
                admin.getRole() + " requested more information for " + saved.getClaimantName() + "'s claim for item " + saved.getFoundItemId());
        return saved;
    }

    /**
     * Resolves the admin note text for an action: prefers the caller-supplied
     * {@code message}/{@code admin_notes}/{@code adminNotes} field (in that order),
     * runs it through the {@link InputSanitizer}, and falls back to the given default
     * when the payload is null or the sanitized text is blank.
     *
     * @param data     optional request payload
     * @param fallback default note used when no usable text is supplied
     * @return the sanitized note, or the fallback
     */
    private String noteFrom(Map<String, Object> data, String fallback) {
        if (data == null) {
            return fallback;
        }
        // Accept any of three accepted key spellings, first non-null wins.
        Object value = data.getOrDefault("message", data.getOrDefault("admin_notes", data.get("adminNotes")));
        String sanitized = sanitizer.sanitizeText(value == null ? "" : String.valueOf(value));
        return sanitized.isBlank() ? fallback : sanitized;
    }

    /**
     * Persists a single audit-log entry describing a privileged action.
     *
     * @param action               machine action code (e.g. {@code CLAIM_APPROVED})
     * @param entityType           type of the affected entity (e.g. {@code Claim})
     * @param entityId             id of the affected entity
     * @param performedBy          email of the acting user
     * @param details              machine-oriented detail string
     * @param humanReadableMessage human-friendly summary for the dashboard feed
     *                             Side effect: writes one {@link AuditLog} row, time-stamped via the clock.
     */
    private void audit(String action, String entityType, String entityId, String performedBy, String details, String humanReadableMessage) {
        AuditLog log = new AuditLog();
        // Compact unique id (10 hex chars from a dash-stripped UUID).
        log.setId("audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setDetails(details);
        log.setHumanReadableMessage(humanReadableMessage);
        log.setCreatedDate(clock.now());
        auditLogs.save(log);
    }
}
