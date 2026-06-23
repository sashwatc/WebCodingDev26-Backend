package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.exception.UnsupportedEntityException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class GenericEntityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericEntityService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LostReportRepository lostReports;
    private final ClaimRepository claims;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final WorkflowService workflow;
    private final MatchmakingService matchmakingService;
    private final FoundItemRepository foundItems;
    private final RecoveryCaseService recoveryCaseService;
    private final CustodyLedgerService custodyLedgerService;
    private final InputSanitizer sanitizer;
    private final RecoveryPulseDispatcher recoveryPulse;

    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(lostReports, claims, notifications, auditLogs, mapper, clock, null, null, null, null, null, new InputSanitizer(), null);
    }

    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            WorkflowService workflow
    ) {
        this(lostReports, claims, notifications, auditLogs, mapper, clock, workflow, null, null, null, null, new InputSanitizer(), null);
    }

    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            MatchmakingService matchmakingService,
            FoundItemRepository foundItems,
            RecoveryCaseService recoveryCaseService,
            CustodyLedgerService custodyLedgerService
    ) {
        this(lostReports, claims, notifications, auditLogs, mapper, clock, null, matchmakingService, foundItems, recoveryCaseService, custodyLedgerService, new InputSanitizer(), null);
    }

    @Autowired
    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            WorkflowService workflow,
            MatchmakingService matchmakingService,
            FoundItemRepository foundItems,
            RecoveryCaseService recoveryCaseService,
            CustodyLedgerService custodyLedgerService,
            InputSanitizer sanitizer,
            RecoveryPulseDispatcher recoveryPulse
    ) {
        this.lostReports = lostReports;
        this.claims = claims;
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
        this.clock = clock;
        this.workflow = workflow;
        this.matchmakingService = matchmakingService;
        this.foundItems = foundItems;
        this.recoveryCaseService = recoveryCaseService;
        this.custodyLedgerService = custodyLedgerService;
        this.sanitizer = sanitizer;
        this.recoveryPulse = recoveryPulse;
    }

    public List<?> list(String entityName) {
        return adapter(entityName).repository().findAll();
    }

    public Object create(String entityName, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        Object entity = mapper.convert(sanitizedData, adapter.type());
        applyCreateDefaults(entity, adapter.prefix());
        validateCreate(entity);
        if (entity instanceof Claim claim && workflow != null) {
            workflow.validateClaim(claim, null);
        }
        Object saved = save(adapter, entity);
        if (saved instanceof LostReport lostReport) {
            return refreshMatches(lostReport);
        }
        if (saved instanceof Claim claim && recoveryCaseService != null) {
            recoveryCaseService.onClaimSubmitted(claim);
        }
        if (saved instanceof Claim claim) {
            markFoundItemForPendingClaim(claim);
            if (isTerminalClaimStatus(claim.getStatus())) {
                markFoundItemForTerminalClaim(claim);
            }
            appendClaimCustodyEvent(claim, "claim_submitted");
            if (recoveryPulse != null) {
                recoveryPulse.claimSubmitted(claim);
            }
        }
        return saved;
    }

    public Object update(String entityName, String id, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Object existing = adapter.repository().findById(id).orElseThrow(() -> new NotFoundException(entityName + " not found"));
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        Object previous = mapper.convert(Map.of(), adapter.type());
        mapper.copyNonNull(existing, previous);
        Object patch = mapper.convert(sanitizedData, adapter.type());
        mapper.copyPresent(sanitizedData, patch, existing, "id", "createdDate");
        validateUpdate(existing);
        applyUpdateTimestamp(existing);
        if (existing instanceof Claim claim && workflow != null) {
            workflow.validateClaim(claim, (Claim) previous);
        }
        Object saved = save(adapter, existing);
        if (saved instanceof Claim claim && recoveryCaseService != null) {
            recoveryCaseService.onClaimSubmitted(claim);
        }
        if (saved instanceof Claim claim && workflow != null) {
            workflow.applyClaimStatusSideEffects(claim, (Claim) previous);
        }
        if (saved instanceof Claim claim && isTerminalClaimStatus(claim.getStatus())) {
            markFoundItemForTerminalClaim(claim);
            appendClaimCustodyEvent(claim, "approved".equalsIgnoreCase(claim.getStatus()) ? "claim_approved" : "returned");
        }
        if (saved instanceof Claim claim && recoveryPulse != null) {
            dispatchClaimStatusNotification(claim, (Claim) previous);
        }
        if (saved instanceof LostReport lostReport) {
            return refreshMatches(lostReport);
        }
        return saved;
    }

    public boolean delete(String entityName, String id) {
        EntityAdapter<?> adapter = adapter(entityName);
        if (!adapter.repository().existsById(id)) {
            throw new NotFoundException(entityName + " not found");
        }
        adapter.repository().deleteById(id);
        return true;
    }

    private EntityAdapter<?> adapter(String entityName) {
        return switch (entityName) {
            case "LostReport" -> new EntityAdapter<>(lostReports, LostReport.class, "lost");
            case "Claim" -> new EntityAdapter<>(claims, Claim.class, "claim");
            case "Notification" -> new EntityAdapter<>(notifications, Notification.class, "notif");
            case "AuditLog" -> new EntityAdapter<>(auditLogs, AuditLog.class, "audit");
            default -> throw new UnsupportedEntityException(entityName);
        };
    }

    private <T> T save(EntityAdapter<T> adapter, Object entity) {
        return adapter.repository().save(adapter.type().cast(entity));
    }

    private void applyCreateDefaults(Object entity, String prefix) {
        String now = clock.now();
        if (entity instanceof LostReport lostReport) {
            lostReport.setId(valueOrGenerated(lostReport.getId(), prefix));
            lostReport.setStatus(valueOrDefault(lostReport.getStatus(), "open"));
            lostReport.setUrgency(valueOrDefault(lostReport.getUrgency(), "medium"));
            lostReport.setCreatedDate(valueOrDefault(lostReport.getCreatedDate(), now));
            lostReport.setUpdatedDate(valueOrDefault(lostReport.getUpdatedDate(), now));
        } else if (entity instanceof Claim claim) {
            claim.setId(valueOrGenerated(claim.getId(), prefix));
            claim.setStatus(valueOrDefault(claim.getStatus(), "submitted"));
            claim.setRiskScore(claim.getRiskScore() == null ? 0 : claim.getRiskScore());
            claim.setCreatedDate(valueOrDefault(claim.getCreatedDate(), now));
            claim.setUpdatedDate(valueOrDefault(claim.getUpdatedDate(), now));
        } else if (entity instanceof Notification notification) {
            notification.setId(valueOrGenerated(notification.getId(), prefix));
            notification.setIsRead(Boolean.TRUE.equals(notification.getIsRead()));
            notification.setCreatedDate(valueOrDefault(notification.getCreatedDate(), now));
            notification.setUpdatedDate(valueOrDefault(notification.getUpdatedDate(), now));
        } else if (entity instanceof AuditLog auditLog) {
            auditLog.setId(valueOrGenerated(auditLog.getId(), prefix));
            auditLog.setCreatedDate(valueOrDefault(auditLog.getCreatedDate(), now));
        }
    }

    private void applyUpdateTimestamp(Object entity) {
        if (entity instanceof LostReport lostReport) {
            lostReport.setUpdatedDate(clock.now());
        } else if (entity instanceof Claim claim) {
            claim.setUpdatedDate(clock.now());
        } else if (entity instanceof Notification notification) {
            notification.setUpdatedDate(clock.now());
        }
    }

    private void validateCreate(Object entity) {
        if (entity instanceof LostReport lostReport) {
            validateLostReport(lostReport);
        }
        if (entity instanceof Claim claim) {
            validateClaimReferencesInventory(claim);
            validateNoDuplicateTerminalClaim(claim);
            if (!isTerminalClaimStatus(claim.getStatus())) {
                validateClaimForm(claim);
            }
        }
    }

    private void validateUpdate(Object entity) {
        if (entity instanceof Claim claim) {
            validateClaimReferencesInventory(claim);
            validateNoDuplicateTerminalClaim(claim);
            if (!isTerminalClaimStatus(claim.getStatus())) {
                validateClaimForm(claim);
            }
        }
    }

    private void validateLostReport(LostReport report) {
        require(report.getTitle(), "Item title is required");
        require(report.getCategory(), "Category is required");
        requireEmail(report.getContactEmail(), "Valid contact email is required");
        if (report.getDateLost() != null && !report.getDateLost().isBlank()) {
            parseDate(report.getDateLost(), "Date lost must use YYYY-MM-DD format");
        }
    }

    private void validateClaimForm(Claim claim) {
        require(claim.getFoundItemId(), "found_item_id is required");
        require(claim.getClaimantName(), "Claimant name is required");
        requireEmail(claim.getClaimantEmail(), "Valid claimant email is required");
        require(claim.getClaimReason(), "Claim reason is required");
        require(claim.getIdentifyingDetails(), "A private identifying detail is required");
    }

    private void validateClaimReferencesInventory(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank()) {
            return;
        }
        if (!foundItems.existsById(claim.getFoundItemId())) {
            throw new NotFoundException("Claims must reference an existing found item.");
        }
    }

    private void validateNoDuplicateTerminalClaim(Claim claim) {
        if (claim.getFoundItemId() == null || claim.getFoundItemId().isBlank() || !isTerminalClaimStatus(claim.getStatus())) {
            return;
        }
        boolean duplicate = claims.findByFoundItemId(claim.getFoundItemId()).stream()
                .anyMatch(existing -> !existing.getId().equals(claim.getId()) && isTerminalClaimStatus(existing.getStatus()));
        if (duplicate) {
            throw new ConflictException("Only one approved or completed claim is allowed for a found item.");
        }
    }

    private boolean isTerminalClaimStatus(String status) {
        return status != null && (status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("completed"));
    }

    private boolean isPendingClaimStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || List.of("submitted", "pending_review", "under_review", "need_more_info").contains(normalized);
    }

    private void dispatchClaimStatusNotification(Claim claim, Claim previous) {
        String status = normalizeStatus(claim.getStatus());
        if (status.equals(normalizeStatus(previous == null ? null : previous.getStatus()))) {
            return;
        }
        switch (status) {
            case "need_more_info" -> recoveryPulse.claimStatusChanged(claim, "claim_more_info_requested");
            case "approved" -> recoveryPulse.claimStatusChanged(claim, "claim_approved");
            case "rejected" -> recoveryPulse.claimStatusChanged(claim, "claim_rejected");
            case "completed" -> recoveryPulse.claimStatusChanged(claim, "item_returned");
            default -> {
            }
        }
    }

    private void appendClaimCustodyEvent(Claim claim, String eventType) {
        if (custodyLedgerService == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank()) {
            return;
        }
        try {
            custodyLedgerService.appendEvent(
                    claim.getFoundItemId(),
                    eventType,
                    claim.getClaimantEmail(),
                    "claimant",
                    "",
                    "Claim workflow event recorded.",
                    claim.getProofPhotoUrl()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to append custody event for claim {}: {}", claim.getId(), exception.getMessage());
        }
    }

    private void markFoundItemForTerminalClaim(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank()) {
            return;
        }
        foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
            if ("approved".equalsIgnoreCase(claim.getStatus()) && !ItemStatus.isArchived(item.getStatus())) {
                item.setStatus(ItemStatus.VERIFIED);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
            if ("completed".equalsIgnoreCase(claim.getStatus())) {
                String confirmedAt = valueOrDefault(claim.getReceivedConfirmedAt(), clock.now());
                item.setStatus("returned");
                item.setClaimConfirmed(true);
                item.setClaimConfirmedAt(confirmedAt);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
                if (recoveryCaseService != null) {
                    recoveryCaseService.markReturned(claim.getId(), claim.getFoundItemId());
                }
            }
        });
    }

    private void markFoundItemForPendingClaim(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank() || !isPendingClaimStatus(claim.getStatus())) {
            return;
        }
        foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
            String status = ItemStatus.canonical(item.getStatus());
            if (!ItemStatus.isArchived(status) && !ItemStatus.VERIFIED.equals(status)) {
                item.setStatus(ItemStatus.CLAIM_PENDING);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
        });
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
    }

    private void requireEmail(String value, String message) {
        if (value == null || value.isBlank() || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }

    private void parseDate(String value, String message) {
        try {
            LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new BadRequestException(message);
        }
    }

    private String valueOrGenerated(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private LostReport refreshMatches(LostReport lostReport) {
        if (lostReport.getId() == null || lostReport.getId().isBlank()) {
            return lostReport;
        }

        try {
            if (recoveryCaseService != null) {
                recoveryCaseService.ensureForLostReport(lostReport);
            }
            if (matchmakingService == null) {
                return lostReport;
            }
            matchmakingService.refreshMatchesForLostReport(lostReport.getId());
            return lostReports.findById(lostReport.getId()).orElse(lostReport);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to refresh matches for lost report {}: {}", lostReport.getId(), exception.getMessage());
            return lostReport;
        }
    }

    private record EntityAdapter<T>(MongoRepository<T, String> repository, Class<T> type, String prefix) {
    }
}
