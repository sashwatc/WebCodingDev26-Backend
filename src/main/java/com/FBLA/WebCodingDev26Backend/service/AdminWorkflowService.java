package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
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

@Service
public class AdminWorkflowService {
    private final FoundItemRepository foundItems;
    private final LostReportRepository lostReports;
    private final ClaimRepository claims;
    private final AuditLogRepository auditLogs;
    private final NotificationRepository notifications;
    private final EmailNotificationService emailNotifications;
    private final ClockService clock;
    private final InputSanitizer sanitizer;

    public AdminWorkflowService(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            AuditLogRepository auditLogs,
            NotificationRepository notifications,
            EmailNotificationService emailNotifications,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this.foundItems = foundItems;
        this.lostReports = lostReports;
        this.claims = claims;
        this.auditLogs = auditLogs;
        this.notifications = notifications;
        this.emailNotifications = emailNotifications;
        this.clock = clock;
        this.sanitizer = sanitizer;
    }

    public Map<String, Object> dashboard() {
        List<Claim> allClaims = claims.findAll();
        List<AuditLog> recentAudit = auditLogs.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
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

    public List<?> listLostReports() {
        return lostReports.findAll();
    }

    public List<Claim> listClaims() {
        return claims.findAll();
    }

    public List<AuditLog> listAuditLogs() {
        return auditLogs.findAll();
    }

    public List<Notification> listNotifications() {
        return notifications.findAll();
    }

    public Claim approveClaim(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        String now = clock.now();

        claim.setStatus("approved");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Claim approved by admin."));
        claim.setUpdatedDate(now);
        Claim savedClaim = claims.save(claim);

        item.setStatus(ItemStatus.VERIFIED);
        item.setClaimConfirmed(true);
        item.setClaimConfirmedAt(now);
        item.setUpdatedDate(now);
        foundItems.save(item);

        emailNotifications.sendClaimApproved(savedClaim, item);
        audit("CLAIM_APPROVED", "Claim", savedClaim.getId(), admin.getEmail(),
                "Approved claim for found item " + item.getId() + ".",
                admin.getRole() + " approved " + savedClaim.getClaimantName() + "'s claim for " + item.getTitle());
        return savedClaim;
    }

    public Claim denyClaim(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        String now = clock.now();

        claim.setStatus("rejected");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Claim denied by admin."));
        claim.setUpdatedDate(now);
        Claim savedClaim = claims.save(claim);

        boolean hasOtherActiveClaim = claims.findByFoundItemId(item.getId()).stream()
                .anyMatch(existing -> !existing.getId().equals(savedClaim.getId()) && isPendingClaim(existing));
        if (!hasOtherActiveClaim) {
            item.setStatus(ItemStatus.FOUND);
            item.setClaimConfirmed(false);
            item.setUpdatedDate(now);
            foundItems.save(item);
        }

        emailNotifications.sendClaimDenied(savedClaim, item);
        audit("CLAIM_DENIED", "Claim", savedClaim.getId(), admin.getEmail(),
                "Denied claim for found item " + item.getId() + ".",
                admin.getRole() + " denied " + savedClaim.getClaimantName() + "'s claim for " + item.getTitle());
        return savedClaim;
    }

    public FoundItem archiveItem(String itemId, AppUser admin, Map<String, Object> data) {
        FoundItem item = foundItems.findById(itemId).orElseThrow(() -> new NotFoundException("Found item not found"));
        item.setStatus(ItemStatus.ARCHIVED);
        item.setUpdatedDate(clock.now());
        FoundItem saved = foundItems.save(item);
        audit("ITEM_ARCHIVED", "FoundItem", saved.getId(), admin.getEmail(), noteFrom(data, "Archived resolved found item."),
                admin.getRole() + " archived item " + saved.getTitle());
        return saved;
    }

    private boolean isPendingClaim(Claim claim) {
        String status = claim.getStatus() == null ? "" : claim.getStatus().trim().toLowerCase(Locale.ROOT);
        return status.isBlank() || List.of("submitted", "pending_review", "under_review", "need_more_info").contains(status);
    }

    public Claim requestMoreInfo(String claimId, AppUser admin, Map<String, Object> data) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        String now = clock.now();
        claim.setStatus("need_more_info");
        claim.setReviewedBy(admin.getEmail());
        claim.setReviewedAt(now);
        claim.setAdminNotes(noteFrom(data, "Admin requested additional information."));
        claim.setUpdatedDate(now);
        Claim saved = claims.save(claim);
        audit("CLAIM_MORE_INFO", "Claim", saved.getId(), admin.getEmail(), saved.getAdminNotes(),
                admin.getRole() + " requested more information for " + saved.getClaimantName() + "'s claim for item " + saved.getFoundItemId());
        return saved;
    }

    private String noteFrom(Map<String, Object> data, String fallback) {
        if (data == null) {
            return fallback;
        }
        Object value = data.getOrDefault("message", data.getOrDefault("admin_notes", data.get("adminNotes")));
        String sanitized = sanitizer.sanitizeText(value == null ? "" : String.valueOf(value));
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private void audit(String action, String entityType, String entityId, String performedBy, String details) {
        audit(action, entityType, entityId, performedBy, details, null);
    }

    private void audit(String action, String entityType, String entityId, String performedBy, String details, String humanReadableMessage) {
        AuditLog log = new AuditLog();
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
