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

/**
 * Generic CRUD facade for the simpler, "table-like" domain entities — {@link LostReport},
 * {@link Claim}, {@link Notification}, and {@link AuditLog} — dispatched by entity name.
 *
 * <p>Beyond plain create/update/delete it owns the cross-cutting business logic those entities
 * share: input sanitization, id/default/timestamp seeding, validation (required fields, email
 * format, claim/inventory integrity, duplicate terminal-claim prevention), and the workflow side
 * effects triggered by lost-report and claim mutations:
 * <ul>
 *   <li>creating/updating a lost report recomputes its matches (and ensures a recovery case);</li>
 *   <li>creating a claim seeds the recovery case, may flip the found item to claim-pending or a
 *       terminal state, records a custody event, notifies, and optionally auto-approves a strong
 *       match;</li>
 *   <li>updating a claim runs workflow validation/side effects, applies terminal-state item changes
 *       and custody events, and dispatches the appropriate status-change notification.</li>
 * </ul>
 *
 * <p>Type dispatch is via an internal {@link EntityAdapter} that pairs each entity name with its
 * repository, class, and id prefix. Many collaborators are optional (null in lighter constructors)
 * so the service degrades gracefully in tests.
 */
@Service
public class GenericEntityService {
    /** Logger for non-fatal side-effect failures (e.g. custody event append). */
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericEntityService.class);
    /** Basic email-shape validator used for contact/claimant email fields. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Lost-report store. */
    private final LostReportRepository lostReports;
    /** Claim store. */
    private final ClaimRepository claims;
    /** Notification store. */
    private final NotificationRepository notifications;
    /** Audit-log store. */
    private final AuditLogRepository auditLogs;
    /** Maps raw request data onto entities and copies present fields for partial updates. */
    private final PatchMapper mapper;
    /** Clock abstraction for created/updated timestamps. */
    private final ClockService clock;
    /** Claim workflow validation + status side-effect engine (optional). */
    private final WorkflowService workflow;
    /** Recomputes lost-report matches after writes (optional). */
    private final MatchmakingService matchmakingService;
    /** Found-item store; used to validate claim references and update item status (optional). */
    private final FoundItemRepository foundItems;
    /** Recovery-case workflow service (optional). */
    private final RecoveryCaseService recoveryCaseService;
    /** Appends custody-chain events for claims (optional). */
    private final CustodyLedgerService custodyLedgerService;
    /** Sanitizes inbound request maps before mapping to entities. */
    private final InputSanitizer sanitizer;
    /** Dispatches claim status-change notifications (optional). */
    private final RecoveryPulseDispatcher recoveryPulse;
    /** Auto-approves a submitted claim that matches an admin-merged strong match (optional). */
    private final StrongMatchAutoApprovalService autoApproval;

    /**
     * Minimal constructor (tests/basic CRUD): repositories + mapper + clock only, default sanitizer,
     * all workflow/matching/recovery collaborators absent.
     */
    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(lostReports, claims, notifications, auditLogs, mapper, clock, null, null, null, null, null, new InputSanitizer(), null, null);
    }

    /**
     * Constructor variant that adds only the claim {@link WorkflowService}.
     */
    public GenericEntityService(
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            WorkflowService workflow
    ) {
        this(lostReports, claims, notifications, auditLogs, mapper, clock, workflow, null, null, null, null, new InputSanitizer(), null, null);
    }

    /**
     * Constructor variant that adds matching/recovery/custody collaborators but no workflow.
     */
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
        this(lostReports, claims, notifications, auditLogs, mapper, clock, null, matchmakingService, foundItems, recoveryCaseService, custodyLedgerService, new InputSanitizer(), null, null);
    }

    /**
     * Primary (Spring-injected) constructor wiring every collaborator.
     */
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
            RecoveryPulseDispatcher recoveryPulse,
            StrongMatchAutoApprovalService autoApproval
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
        this.autoApproval = autoApproval;
    }

    /**
     * Lists all rows of the named entity.
     *
     * @param entityName one of LostReport / Claim / Notification / AuditLog
     * @return every persisted entity of that type
     * @throws com.FBLA.WebCodingDev26Backend.exception.UnsupportedEntityException for unknown names
     */
    public List<?> list(String entityName) {
        return adapter(entityName).repository().findAll();
    }

    /**
     * Creates a new entity of the named type from a raw request body, applying defaults,
     * validation, and type-specific workflow side effects.
     *
     * <p>Pipeline: sanitize → map to entity → seed id/status/timestamps → validate → (claims only)
     * run workflow validation → persist. Post-persist: a LostReport recomputes matches; a Claim
     * seeds its recovery case, may flag the found item as claim-pending (or terminal), records a
     * {@code claim_submitted} custody event, fires a submitted notification, and may be
     * auto-approved when it matches an admin-merged strong match.
     *
     * @param entityName the entity type name
     * @param data       raw client payload
     * @return the saved entity (or its refreshed/auto-approved variant for lost reports/claims)
     * @throws com.FBLA.WebCodingDev26Backend.exception.BadRequestException on validation failure
     * @throws NotFoundException if a claim references a non-existent found item
     * @throws ConflictException if it would create a duplicate terminal claim
     */
    public Object create(String entityName, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        Object entity = mapper.convert(sanitizedData, adapter.type());
        // Seed id, default status, and timestamps for the new record.
        applyCreateDefaults(entity, adapter.prefix());
        validateCreate(entity);
        // Claims get an extra workflow validation pass (no "previous" on create).
        if (entity instanceof Claim claim && workflow != null) {
            workflow.validateClaim(claim, null);
        }
        Object saved = save(adapter, entity);
        // Lost reports: immediately (re)compute their possible matches.
        if (saved instanceof LostReport lostReport) {
            return refreshMatches(lostReport);
        }
        // Claim recovery case bootstrap.
        if (saved instanceof Claim claim && recoveryCaseService != null) {
            recoveryCaseService.onClaimSubmitted(claim);
        }
        if (saved instanceof Claim claim) {
            // Reflect the claim on the found item (pending, or terminal if already approved/completed).
            markFoundItemForPendingClaim(claim);
            if (isTerminalClaimStatus(claim.getStatus())) {
                markFoundItemForTerminalClaim(claim);
            }
            // Record the submission in the custody chain and notify.
            appendClaimCustodyEvent(claim, "claim_submitted");
            if (recoveryPulse != null) {
                recoveryPulse.claimSubmitted(claim);
            }
            // If this claim matches an admin-merged strong match for the same owner,
            // auto-approve it and start the return process; otherwise leave it for
            // normal manual review.
            if (autoApproval != null) {
                Claim autoApproved = autoApproval.maybeAutoApprove(claim);
                if (autoApproved != null) {
                    return autoApproved;
                }
            }
        }
        return saved;
    }

    /**
     * Applies a partial update to an existing entity, running validation and claim side effects.
     *
     * <p>Pipeline: load existing → snapshot a "previous" copy (for claim diffing) → sanitize &
     * patch only present fields (never id/createdDate) → validate → stamp updatedDate → (claims)
     * workflow-validate against previous → persist. Post-persist for claims: refresh recovery case,
     * apply workflow status side effects, on terminal status update the found item + custody event,
     * and dispatch the status-change notification. A lost report recomputes matches.
     *
     * @param entityName the entity type name
     * @param id         the entity id
     * @param data       raw patch payload
     * @return the saved entity (refreshed for lost reports)
     * @throws NotFoundException if no entity has that id
     * @throws com.FBLA.WebCodingDev26Backend.exception.BadRequestException on validation failure
     * @throws ConflictException on duplicate terminal claim
     */
    public Object update(String entityName, String id, Map<String, Object> data) {
        EntityAdapter<?> adapter = adapter(entityName);
        Object existing = adapter.repository().findById(id).orElseThrow(() -> new NotFoundException(entityName + " not found"));
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        // Capture the pre-update state so claim status transitions can be diffed.
        Object previous = mapper.convert(Map.of(), adapter.type());
        mapper.copyNonNull(existing, previous);
        Object patch = mapper.convert(sanitizedData, adapter.type());
        // Overlay only provided fields onto the existing entity; protect id/createdDate.
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
        // Run any status-driven workflow side effects (using the prior status).
        if (saved instanceof Claim claim && workflow != null) {
            workflow.applyClaimStatusSideEffects(claim, (Claim) previous);
        }
        // Terminal claim: update the found item and record approved/returned custody event.
        if (saved instanceof Claim claim && isTerminalClaimStatus(claim.getStatus())) {
            markFoundItemForTerminalClaim(claim);
            appendClaimCustodyEvent(claim, "approved".equalsIgnoreCase(claim.getStatus()) ? "claim_approved" : "returned");
        }
        // Notify the claimant only when the status actually changed.
        if (saved instanceof Claim claim && recoveryPulse != null) {
            dispatchClaimStatusNotification(claim, (Claim) previous);
        }
        if (saved instanceof LostReport lostReport) {
            return refreshMatches(lostReport);
        }
        return saved;
    }

    /**
     * Hard-deletes the named entity by id.
     *
     * @param entityName the entity type name
     * @param id         the entity id
     * @return {@code true} on success
     * @throws NotFoundException if the entity does not exist
     */
    public boolean delete(String entityName, String id) {
        EntityAdapter<?> adapter = adapter(entityName);
        if (!adapter.repository().existsById(id)) {
            throw new NotFoundException(entityName + " not found");
        }
        adapter.repository().deleteById(id);
        return true;
    }

    /**
     * Resolves an entity name to its repository/type/id-prefix adapter.
     *
     * @param entityName one of the supported names
     * @return the matching {@link EntityAdapter}
     * @throws UnsupportedEntityException for any unrecognized name
     */
    private EntityAdapter<?> adapter(String entityName) {
        return switch (entityName) {
            case "LostReport" -> new EntityAdapter<>(lostReports, LostReport.class, "lost");
            case "Claim" -> new EntityAdapter<>(claims, Claim.class, "claim");
            case "Notification" -> new EntityAdapter<>(notifications, Notification.class, "notif");
            case "AuditLog" -> new EntityAdapter<>(auditLogs, AuditLog.class, "audit");
            default -> throw new UnsupportedEntityException(entityName);
        };
    }

    /** Persists {@code entity} through the adapter's repository, casting to the adapter's type. */
    private <T> T save(EntityAdapter<T> adapter, Object entity) {
        return adapter.repository().save(adapter.type().cast(entity));
    }

    /**
     * Seeds creation defaults per entity type: a generated prefixed id when missing, a default
     * status, and created/updated timestamps (and per-type fields like claim risk score 0,
     * notification read=false). Existing values are preserved.
     *
     * @param entity the entity being created
     * @param prefix the id prefix for generated ids
     */
    private void applyCreateDefaults(Object entity, String prefix) {
        String now = clock.now();
        if (entity instanceof LostReport lostReport) {
            // New lost reports default to "open" with "medium" urgency.
            lostReport.setId(valueOrGenerated(lostReport.getId(), prefix));
            lostReport.setStatus(valueOrDefault(lostReport.getStatus(), "open"));
            lostReport.setUrgency(valueOrDefault(lostReport.getUrgency(), "medium"));
            lostReport.setCreatedDate(valueOrDefault(lostReport.getCreatedDate(), now));
            lostReport.setUpdatedDate(valueOrDefault(lostReport.getUpdatedDate(), now));
        } else if (entity instanceof Claim claim) {
            // New claims default to "submitted" with zero risk score.
            claim.setId(valueOrGenerated(claim.getId(), prefix));
            claim.setStatus(valueOrDefault(claim.getStatus(), "submitted"));
            claim.setRiskScore(claim.getRiskScore() == null ? 0 : claim.getRiskScore());
            claim.setCreatedDate(valueOrDefault(claim.getCreatedDate(), now));
            claim.setUpdatedDate(valueOrDefault(claim.getUpdatedDate(), now));
        } else if (entity instanceof Notification notification) {
            // New notifications default to unread.
            notification.setId(valueOrGenerated(notification.getId(), prefix));
            notification.setIsRead(Boolean.TRUE.equals(notification.getIsRead()));
            notification.setCreatedDate(valueOrDefault(notification.getCreatedDate(), now));
            notification.setUpdatedDate(valueOrDefault(notification.getUpdatedDate(), now));
        } else if (entity instanceof AuditLog auditLog) {
            // Audit logs are append-only: id + created timestamp only.
            auditLog.setId(valueOrGenerated(auditLog.getId(), prefix));
            auditLog.setCreatedDate(valueOrDefault(auditLog.getCreatedDate(), now));
        }
    }

    /** Stamps the updatedDate on entity types that track it (lost report, claim, notification). */
    private void applyUpdateTimestamp(Object entity) {
        if (entity instanceof LostReport lostReport) {
            lostReport.setUpdatedDate(clock.now());
        } else if (entity instanceof Claim claim) {
            claim.setUpdatedDate(clock.now());
        } else if (entity instanceof Notification notification) {
            notification.setUpdatedDate(clock.now());
        }
    }

    /** Runs create-time validation: lost-report field checks, or claim integrity + form checks. */
    private void validateCreate(Object entity) {
        if (entity instanceof LostReport lostReport) {
            validateLostReport(lostReport);
        }
        if (entity instanceof Claim claim) {
            validateClaimReferencesInventory(claim);
            validateNoDuplicateTerminalClaim(claim);
            // Full form validation only for non-terminal claims (terminal records may be seeded).
            if (!isTerminalClaimStatus(claim.getStatus())) {
                validateClaimForm(claim);
            }
        }
    }

    /** Runs update-time validation for claims (inventory reference, no duplicate terminal, form). */
    private void validateUpdate(Object entity) {
        if (entity instanceof Claim claim) {
            validateClaimReferencesInventory(claim);
            validateNoDuplicateTerminalClaim(claim);
            if (!isTerminalClaimStatus(claim.getStatus())) {
                validateClaimForm(claim);
            }
        }
    }

    /**
     * Validates a lost report: title, category, and a well-formed contact email are required; if a
     * date-lost is present it must be ISO {@code YYYY-MM-DD}.
     *
     * @throws com.FBLA.WebCodingDev26Backend.exception.BadRequestException when a rule fails
     */
    private void validateLostReport(LostReport report) {
        require(report.getTitle(), "Item title is required");
        require(report.getCategory(), "Category is required");
        requireEmail(report.getContactEmail(), "Valid contact email is required");
        if (report.getDateLost() != null && !report.getDateLost().isBlank()) {
            parseDate(report.getDateLost(), "Date lost must use YYYY-MM-DD format");
        }
    }

    /**
     * Validates a (non-terminal) claim form: found-item id, claimant name, valid claimant email,
     * claim reason, and a private identifying detail are all required.
     *
     * @throws com.FBLA.WebCodingDev26Backend.exception.BadRequestException when a field is missing/invalid
     */
    private void validateClaimForm(Claim claim) {
        require(claim.getFoundItemId(), "found_item_id is required");
        require(claim.getClaimantName(), "Claimant name is required");
        requireEmail(claim.getClaimantEmail(), "Valid claimant email is required");
        require(claim.getClaimReason(), "Claim reason is required");
        require(claim.getIdentifyingDetails(), "A private identifying detail is required");
    }

    /**
     * Ensures a claim's referenced found item actually exists in inventory.
     *
     * <p>No-op when the found-item store is absent or no id is set.
     *
     * @throws NotFoundException if the referenced found item does not exist
     */
    private void validateClaimReferencesInventory(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank()) {
            return;
        }
        if (!foundItems.existsById(claim.getFoundItemId())) {
            throw new NotFoundException("Claims must reference an existing found item.");
        }
    }

    /**
     * Enforces at most one terminal (approved/completed) claim per found item.
     *
     * <p>Only applies when this claim is itself terminal; checks other claims on the same item.
     *
     * @throws ConflictException if another terminal claim already exists for the item
     */
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

    /** @return true when the claim status is terminal: approved or completed (case-insensitive). */
    private boolean isTerminalClaimStatus(String status) {
        return status != null && (status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("completed"));
    }

    /** @return true when the claim status is pending/in-progress (blank, submitted, pending_review, under_review, need_more_info). */
    private boolean isPendingClaimStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || List.of("submitted", "pending_review", "under_review", "need_more_info").contains(normalized);
    }

    /**
     * Dispatches the right claimant notification when a claim's status changes.
     *
     * <p>No-op if the status is unchanged from {@code previous}. Otherwise maps the new status to a
     * pulse event: need_more_info → more-info-requested, approved → approved, rejected → rejected,
     * completed → item-returned (other statuses produce no notification).
     *
     * @param claim    the saved claim
     * @param previous the pre-update claim state (may be null)
     */
    private void dispatchClaimStatusNotification(Claim claim, Claim previous) {
        String status = normalizeStatus(claim.getStatus());
        // Skip when the status did not actually transition.
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

    /**
     * Appends a claim-related event to the found item's custody ledger (best-effort).
     *
     * <p>No-op when the ledger service is absent or no found item is referenced. The claimant is
     * recorded as the actor; failures are logged and swallowed so they never break the claim flow.
     *
     * @param claim     the claim
     * @param eventType custody event type (e.g. claim_submitted, claim_approved, returned)
     */
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

    /**
     * Reflects a terminal claim onto its found item.
     *
     * <p>For an {@code approved} claim (and a non-archived item) the item becomes
     * {@link ItemStatus#VERIFIED}. For a {@code completed} claim the item becomes {@code returned},
     * is marked claim-confirmed with a confirmation timestamp, and the recovery case is marked
     * returned. No-op when the found item store/id is absent.
     *
     * @param claim the terminal claim
     */
    private void markFoundItemForTerminalClaim(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank()) {
            return;
        }
        foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
            // Approved: item is verified as belonging to the claimant (unless already archived).
            if ("approved".equalsIgnoreCase(claim.getStatus()) && !ItemStatus.isArchived(item.getStatus())) {
                item.setStatus(ItemStatus.VERIFIED);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
            // Completed: item has been physically returned; record confirmation + close recovery.
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

    /**
     * Marks the found item as {@link ItemStatus#CLAIM_PENDING} while a claim is pending review.
     *
     * <p>No-op unless the claim is in a pending status. Skips items that are archived or already
     * verified so a terminal state is never downgraded.
     *
     * @param claim the pending claim
     */
    private void markFoundItemForPendingClaim(Claim claim) {
        if (foundItems == null || claim.getFoundItemId() == null || claim.getFoundItemId().isBlank() || !isPendingClaimStatus(claim.getStatus())) {
            return;
        }
        foundItems.findById(claim.getFoundItemId()).ifPresent(item -> {
            String status = ItemStatus.canonical(item.getStatus());
            // Only flag items that aren't already archived/verified.
            if (!ItemStatus.isArchived(status) && !ItemStatus.VERIFIED.equals(status)) {
                item.setStatus(ItemStatus.CLAIM_PENDING);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
        });
    }

    /** Requires a non-blank string; @throws BadRequestException with {@code message} otherwise. */
    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
    }

    /** Requires a non-blank, well-formed email; @throws BadRequestException with {@code message} otherwise. */
    private void requireEmail(String value, String message) {
        if (value == null || value.isBlank() || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }

    /** Validates ISO {@code YYYY-MM-DD} date syntax; @throws BadRequestException with {@code message} if unparseable. */
    private void parseDate(String value, String message) {
        try {
            LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new BadRequestException(message);
        }
    }

    /** @return {@code value} when non-blank, else a generated {@code prefix_<10-hex>} id. */
    private String valueOrGenerated(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : value;
    }

    /** @return {@code value} when non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** @return a null-safe, trimmed, lowercased status string. */
    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Ensures a recovery case exists and recomputes matches for a saved lost report, returning the
     * freshly reloaded report.
     *
     * <p>No-op (returns the input) when the report has no id or matchmaking is unwired. Any failure
     * is logged and the original report is returned, so a matching error never fails the write.
     *
     * @param lostReport the saved lost report
     * @return the reloaded report (with refreshed matches) or the original on skip/failure
     */
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
            // Recompute possible matches, then return the persisted, updated report.
            matchmakingService.refreshMatchesForLostReport(lostReport.getId());
            return lostReports.findById(lostReport.getId()).orElse(lostReport);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to refresh matches for lost report {}: {}", lostReport.getId(), exception.getMessage());
            return lostReport;
        }
    }

    /**
     * Pairs an entity name's repository with its concrete class and id prefix, enabling generic
     * type-safe CRUD dispatch.
     */
    private record EntityAdapter<T>(MongoRepository<T, String> repository, Class<T> type, String prefix) {
    }
}
