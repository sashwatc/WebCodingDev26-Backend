package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages the lifecycle of a {@link RecoveryCase} — the staff-facing record that tracks the
 * effort to reunite a {@link LostReport} with its owner, from "open" through to "returned".
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Lazily creating exactly one recovery case per lost report and keeping it in sync
 *       ({@link #ensureForLostReport}, {@link #getByLostReport}).</li>
 *   <li>Refreshing the deterministic recovery plan (likely campus zones + narrative) and
 *       auto-advancing the case to "match_identified" when typed matches exist
 *       ({@link #refreshForLostReport}).</li>
 *   <li>Driving status transitions in response to claim/return-pass lifecycle events
 *       ({@link #onClaimSubmitted}, {@link #onMatchLinked}, {@link #markPickupReady},
 *       {@link #markReturned}).</li>
 *   <li>Validating, patching, and staff-assigning cases, and emitting an audit trail.</li>
 *   <li>Firing Recovery Pulse notifications whenever a case's status actually changes.</li>
 * </ul>
 *
 * <p>Collaborators: {@link RecoveryCaseRepository}, {@link LostReportRepository},
 * {@link AuditLogRepository}, {@link RecoveryPlanningService} (zone scoring),
 * {@link PatchMapper} (partial-update mapping), {@link InputSanitizer},
 * {@link ClockService}, and {@link RecoveryPulseDispatcher} (notifications).
 */
@Service
public class RecoveryCaseService {
    /** Logger used to record (and swallow) notification-dispatch failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryCaseService.class);
    /** Lightweight email-shape validator for lost-report contact emails. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** The closed set of valid recovery-case statuses; any other status is rejected by validation. */
    private static final Set<String> CASE_STATUSES = Set.of(
            "open",
            "match_identified",
            "claim_in_review",
            "pickup_ready",
            "returned",
            "closed",
            "archived",
            "paused"
    );

    /** Persistence for recovery cases. */
    private final RecoveryCaseRepository cases;
    /** Persistence for lost reports (the parent record a case references). */
    private final LostReportRepository lostReports;
    /** Optional audit-log sink; may be null (legacy constructor) in which case auditing is skipped. */
    private final AuditLogRepository auditLogs;
    /** Computes the deterministic likely-zone recommendations that drive the recovery plan. */
    private final RecoveryPlanningService planningService;
    /** Converts/copies request maps onto entity instances for partial updates. */
    private final PatchMapper mapper;
    /** Timestamp provider (injectable for deterministic tests). */
    private final ClockService clock;
    /** Sanitizes untrusted request maps before they are mapped onto entities. */
    private final InputSanitizer sanitizer;
    /** Optional notification dispatcher; null disables status-change notifications. */
    private final RecoveryPulseDispatcher recoveryPulse;

    /**
     * Minimal convenience constructor (no audit log, no notifications). Delegates to the full
     * constructor with a fresh {@link InputSanitizer} and null audit/pulse collaborators.
     */
    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            LostReportRepository lostReports,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(cases, lostReports, null, planningService, mapper, clock, new InputSanitizer(), null);
    }

    /**
     * Convenience constructor with auditing and an explicit sanitizer but no notification
     * dispatcher. Delegates to the full constructor with a null {@link RecoveryPulseDispatcher}.
     */
    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            LostReportRepository lostReports,
            AuditLogRepository auditLogs,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this(cases, lostReports, auditLogs, planningService, mapper, clock, sanitizer, null);
    }

    /**
     * Primary (Spring-wired) constructor. Stores all collaborators; defends against a null
     * sanitizer by substituting a fresh {@link InputSanitizer}. The audit log and recovery-pulse
     * dispatcher are allowed to be null (auditing / notifications then become no-ops).
     */
    @Autowired
    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            LostReportRepository lostReports,
            AuditLogRepository auditLogs,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer,
            RecoveryPulseDispatcher recoveryPulse
    ) {
        this.cases = cases;
        this.lostReports = lostReports;
        this.auditLogs = auditLogs;
        this.planningService = planningService;
        this.mapper = mapper;
        this.clock = clock;
        this.sanitizer = sanitizer == null ? new InputSanitizer() : sanitizer;
        this.recoveryPulse = recoveryPulse;
    }

    /** Returns all recovery cases. */
    public List<RecoveryCase> list() {
        return cases.findAll();
    }

    /**
     * Fetches a single case by id.
     *
     * @throws NotFoundException if no case exists for the id
     */
    public RecoveryCase get(String id) {
        return cases.findById(id).orElseThrow(() -> new NotFoundException("Recovery case not found"));
    }

    /**
     * Returns the case for a lost report, lazily creating one if none exists yet.
     *
     * @param lostReportId id of the parent lost report
     * @return the existing or newly-created case
     * @throws NotFoundException if no lost report exists for the id (only checked on the create path)
     */
    public RecoveryCase getByLostReport(String lostReportId) {
        return cases.findByLostReportId(lostReportId)
                .orElseGet(() -> ensureForLostReport(lostReports.findById(lostReportId)
                        .orElseThrow(() -> new NotFoundException("Lost report not found"))));
    }

    /**
     * Ensures a case exists for an existing lost report and writes an audit entry attributing the
     * action to the given admin.
     *
     * @param lostReportId id of an already-persisted lost report
     * @param adminEmail   actor recorded in the audit log
     * @return the existing or newly-created case
     * @throws NotFoundException if the lost report does not exist
     */
    public RecoveryCase createFromLostReport(String lostReportId, String adminEmail) {
        LostReport report = lostReports.findById(lostReportId).orElseThrow(() -> new NotFoundException("Lost report not found"));
        RecoveryCase recoveryCase = ensureForLostReport(report);
        audit("RECOVERY_CASE_CREATED", "RecoveryCase", recoveryCase.getId(), adminEmail,
                "Created or confirmed recovery case for lost report " + report.getId() + ".");
        return recoveryCase;
    }

    /**
     * Creates a brand-new lost report from a raw request map and immediately opens a case for it.
     *
     * <p>Steps: sanitize the input map, map it to a {@link LostReport}, fill in defaults, validate
     * required fields, persist the report (auditing the creation), then ensure a case exists
     * (auditing that too).
     *
     * @param data       untrusted request map describing the lost report
     * @param adminEmail actor recorded in the two audit entries
     * @return the recovery case opened for the new report
     * @throws BadRequestException if validation fails (missing title/category/email)
     */
    public RecoveryCase createFromLostReportData(Map<String, Object> data, String adminEmail) {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        LostReport report = mapper.convert(sanitized, LostReport.class);
        applyLostReportDefaults(report);
        validateLostReport(report);
        LostReport savedReport = lostReports.save(report);
        audit("LOST_REPORT_CREATED_FOR_RECOVERY", "LostReport", savedReport.getId(), adminEmail,
                "Created lost report before opening a recovery case.");
        RecoveryCase recoveryCase = ensureForLostReport(savedReport);
        audit("RECOVERY_CASE_CREATED", "RecoveryCase", recoveryCase.getId(), adminEmail,
                "Created recovery case for new lost report " + savedReport.getId() + ".");
        return recoveryCase;
    }

    /**
     * Idempotently returns the single case bound to a lost report, creating and seeding a new one
     * if absent. The newly-created case gets a generated id, a human-readable case code of the form
     * {@code PVHS-RM-YYYYMMDD-XXXX}, the "pvhs" tenant, copies of the report's hub/zone, an initial
     * "open" status, priority defaulted from the report's urgency, a summary from the report title,
     * a placeholder recovery plan, and the report's demo flag.
     *
     * @param report a persisted lost report (must have an id)
     * @return the existing or newly-created case
     * @throws BadRequestException if the report is null or has no id
     */
    public RecoveryCase ensureForLostReport(LostReport report) {
        if (report == null || isBlank(report.getId())) {
            throw new BadRequestException("Recovery cases must reference a saved lost report.");
        }
        // Return the existing case if one is already bound to this report; otherwise build a new one.
        return cases.findByLostReportId(report.getId()).orElseGet(() -> {
            String now = clock.now();
            RecoveryCase recoveryCase = new RecoveryCase();
            recoveryCase.setId("case_" + shortId());
            // Case code = PVHS-RM-<YYYYMMDD from createdDate>-<first 4 chars of a fresh id, upper-cased>.
            recoveryCase.setCaseCode("PVHS-RM-" + Instant.parse(now).toString().substring(0, 10).replace("-", "") + "-" + shortId().substring(0, 4).toUpperCase());
            recoveryCase.setTenantId("pvhs");
            recoveryCase.setLostReportId(report.getId());
            recoveryCase.setEventHubId(report.getEventHubId());
            recoveryCase.setCampusZoneId(report.getCampusZoneId());
            recoveryCase.setStatus("open");
            // Priority mirrors report urgency, defaulting to "medium".
            recoveryCase.setPriority(valueOrDefault(report.getUrgency(), "medium"));
            recoveryCase.setSummary(valueOrDefault(report.getTitle(), "Lost item recovery case"));
            recoveryCase.setRecoveryPlan("Staff are checking likely locations.");
            // Inherit the demo flag so demo cases stay segregated from real data.
            recoveryCase.setIsDemo(Boolean.TRUE.equals(report.getIsDemo()));
            recoveryCase.setCreatedDate(now);
            recoveryCase.setUpdatedDate(now);
            return cases.save(recoveryCase);
        });
    }

    /**
     * Overload of {@link #refreshForLostReport(String, String)} with no audit actor.
     */
    public RecoveryCase refreshForLostReport(String lostReportId) {
        return refreshForLostReport(lostReportId, null);
    }

    /**
     * Recomputes a case's deterministic recovery plan from its lost report and advances status when
     * a match is found.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the report (404 if missing) and ensure its case exists.</li>
     *   <li>Ask {@link RecoveryPlanningService} for ranked zone recommendations.</li>
     *   <li>Re-sync the case's hub/zone, store per-zone summary strings ("label - score%"), and the
     *       rendered narrative plan text.</li>
     *   <li>If the report carries typed matches, point the case at the top match's found item and,
     *       only while still "open", promote it to "match_identified".</li>
     *   <li>Save, fire a status-change notification, and write an audit entry.</li>
     * </ol>
     *
     * @param lostReportId id of the parent lost report
     * @param adminEmail   actor recorded in the audit log (may be null)
     * @return the refreshed, persisted case
     * @throws NotFoundException if the lost report does not exist
     */
    public RecoveryCase refreshForLostReport(String lostReportId, String adminEmail) {
        LostReport report = lostReports.findById(lostReportId).orElseThrow(() -> new NotFoundException("Lost report not found"));
        RecoveryCase recoveryCase = ensureForLostReport(report);
        // Capture status before mutation so we only notify on an actual transition.
        String previousStatus = recoveryCase.getStatus();
        // Deterministic zone scoring drives both the summaries and the narrative plan text.
        List<RecoveryPlanningService.ZoneRecommendation> recommendations = planningService.recommendZones(report);
        recoveryCase.setEventHubId(report.getEventHubId());
        recoveryCase.setCampusZoneId(report.getCampusZoneId());
        // Compact per-zone summaries shown in the UI, e.g. "Main Gym - 72%".
        recoveryCase.setLikelyZoneSummaries(recommendations.stream()
                .map(zone -> zone.zoneLabel() + " - " + zone.score() + "%")
                .toList());
        recoveryCase.setRecoveryPlan(planText(recommendations));

        // If the report already carries candidate matches, latch onto the strongest one.
        List<MatchSuggestion> matches = typedMatches(report);
        if (!matches.isEmpty()) {
            recoveryCase.setSelectedFoundItemId(matches.get(0).getFoundItemId());
            // Only promote a still-open case; never downgrade a case already further along.
            if ("open".equals(recoveryCase.getStatus())) {
                recoveryCase.setStatus("match_identified");
            }
        }
        recoveryCase.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(recoveryCase);
        notifyCaseStatusChanged(saved, previousStatus);
        audit("RECOVERY_PLAN_REFRESHED", "RecoveryCase", saved.getId(), adminEmail,
                "Refreshed deterministic recovery plan for lost report " + report.getId() + ".");
        return saved;
    }

    /** Overload of {@link #update(String, Map, String)} with no audit actor. */
    public RecoveryCase update(String id, Map<String, Object> data) {
        return update(id, data, null);
    }

    /**
     * Applies a partial update to a case from a raw request map.
     *
     * <p>Steps: load the case, sanitize the input, map it to a patch entity, then copy only the
     * present (non-null) fields onto the existing case — while protecting the immutable
     * {@code id}, {@code createdDate}, {@code lostReportId}, and {@code caseCode} from being
     * overwritten. The resulting status is validated against {@link #CASE_STATUSES}. On save, a
     * status-change notification and an audit entry are emitted.
     *
     * @param id         id of the case to update
     * @param data       untrusted patch map
     * @param adminEmail actor recorded in the audit log (may be null)
     * @return the saved case
     * @throws NotFoundException   if the case does not exist
     * @throws BadRequestException if the resulting status is not a recognized value
     */
    public RecoveryCase update(String id, Map<String, Object> data, String adminEmail) {
        RecoveryCase existing = get(id);
        String previousStatus = existing.getStatus();
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        RecoveryCase patch = mapper.convert(sanitized, RecoveryCase.class);
        // Copy present fields from patch onto existing, but never the four protected immutable keys.
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "lostReportId", "caseCode");
        validateCaseStatus(existing.getStatus());
        existing.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(existing);
        notifyCaseStatusChanged(saved, previousStatus);
        audit("RECOVERY_CASE_UPDATED", "RecoveryCase", saved.getId(), adminEmail, "Updated recovery case fields.");
        return saved;
    }

    /**
     * Assigns a staff member to a case. Accepts the assignee under any of the keys
     * {@code assigned_to}, {@code assignedTo}, or {@code email}.
     *
     * @param id         id of the case
     * @param data       request map containing the assignee email
     * @param adminEmail actor recorded in the audit log
     * @return the saved case
     * @throws NotFoundException   if the case does not exist
     * @throws BadRequestException if no assignee was supplied
     */
    public RecoveryCase assignStaff(String id, Map<String, Object> data, String adminEmail) {
        RecoveryCase existing = get(id);
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        String assignedTo = stringValue(sanitized, "assigned_to", "assignedTo", "email");
        if (isBlank(assignedTo)) {
            throw new BadRequestException("assigned_to is required.");
        }
        existing.setAssignedTo(assignedTo);
        existing.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(existing);
        audit("RECOVERY_CASE_ASSIGNED", "RecoveryCase", saved.getId(), adminEmail,
                "Assigned recovery case to " + assignedTo + ".");
        return saved;
    }

    /**
     * Reacts to a newly-submitted claim by linking it to every case that targets the same found
     * item and advancing those cases into "claim_in_review".
     *
     * <p>For each matching case: record the claim id, and unless the case is already "returned" or
     * "closed", set status to "claim_in_review", then save and notify on any status change.
     * No-op when the claim has no found item id.
     *
     * @param claim the submitted claim
     */
    public void onClaimSubmitted(Claim claim) {
        if (isBlank(claim.getFoundItemId())) {
            return;
        }
        // Find every case currently pointing at this found item.
        cases.findAll().stream()
                .filter(recoveryCase -> claim.getFoundItemId().equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    String previousStatus = recoveryCase.getStatus();
                    recoveryCase.setLinkedClaimId(claim.getId());
                    // Don't reopen a case that has already been completed (returned/closed).
                    if (!"returned".equals(recoveryCase.getStatus()) && !"closed".equals(recoveryCase.getStatus())) {
                        recoveryCase.setStatus("claim_in_review");
                    }
                    recoveryCase.setUpdatedDate(clock.now());
                    RecoveryCase saved = cases.save(recoveryCase);
                    notifyCaseStatusChanged(saved, previousStatus);
                });
    }

    /**
     * An admin linked a confirmed found item to this lost report. Point the case
     * at that found item and advance it to "match identified" so the return
     * process is underway. Idempotent; never downgrades a case already further
     * along (claim review / pickup / returned).
     */
    public RecoveryCase onMatchLinked(LostReport report, String foundItemId) {
        // Guard: need a saved report and a concrete found item to link.
        if (report == null || isBlank(report.getId()) || isBlank(foundItemId)) {
            return null;
        }
        RecoveryCase recoveryCase = ensureForLostReport(report);
        String previousStatus = recoveryCase.getStatus();
        recoveryCase.setSelectedFoundItemId(foundItemId);
        // Only set "match_identified" from the early states; later states (claim/pickup/returned) are left intact.
        if (Set.of("open", "match_identified").contains(normalize(recoveryCase.getStatus()))) {
            recoveryCase.setStatus("match_identified");
        }
        recoveryCase.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(recoveryCase);
        notifyCaseStatusChanged(saved, previousStatus);
        return saved;
    }

    /**
     * Marks the case(s) tied to a claim or found item as "pickup_ready" (a Return Pass was issued).
     *
     * @param claimId     id of the linked claim
     * @param foundItemId id of the selected found item
     */
    public void markPickupReady(String claimId, String foundItemId) {
        updateCaseByClaimOrItem(claimId, foundItemId, "pickup_ready");
    }

    /**
     * Marks the case(s) tied to a claim or found item as "returned" (item handed back to its owner).
     *
     * @param claimId     id of the linked claim
     * @param foundItemId id of the selected found item
     */
    public void markReturned(String claimId, String foundItemId) {
        updateCaseByClaimOrItem(claimId, foundItemId, "returned");
    }

    /**
     * Shared helper that sets a target status on every case whose linked claim id OR selected found
     * item id matches the arguments. Back-fills the linked claim id if it was missing, saves, and
     * notifies on each real status change.
     *
     * @param claimId     claim id to match / back-fill
     * @param foundItemId found item id to match
     * @param status      the new status to apply
     */
    private void updateCaseByClaimOrItem(String claimId, String foundItemId, String status) {
        cases.findAll().stream()
                .filter(recoveryCase -> claimId.equals(recoveryCase.getLinkedClaimId()) || foundItemId.equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    String previousStatus = recoveryCase.getStatus();
                    // Preserve an existing linked claim id; otherwise adopt the supplied one.
                    recoveryCase.setLinkedClaimId(valueOrDefault(recoveryCase.getLinkedClaimId(), claimId));
                    recoveryCase.setStatus(status);
                    recoveryCase.setUpdatedDate(clock.now());
                    RecoveryCase saved = cases.save(recoveryCase);
                    notifyCaseStatusChanged(saved, previousStatus);
                });
    }

    /**
     * Dispatches a Recovery Pulse notification when a case's status genuinely changed.
     *
     * <p>No-op if no dispatcher is wired or the normalized status is unchanged. Any dispatch
     * exception is caught and logged so a notification failure never breaks the persistence flow.
     *
     * @param recoveryCase   the saved case (carrying its new status)
     * @param previousStatus the status before the mutation
     */
    private void notifyCaseStatusChanged(RecoveryCase recoveryCase, String previousStatus) {
        // Skip when notifications are disabled or the status did not actually move.
        if (recoveryPulse == null || normalize(recoveryCase.getStatus()).equals(normalize(previousStatus))) {
            return;
        }
        try {
            recoveryPulse.recoveryCaseStatusUpdated(recoveryCase, previousStatus);
        } catch (RuntimeException exception) {
            // Notifications are best-effort; log and continue rather than failing the caller.
            LOGGER.warn("Unable to dispatch recovery case notification for case {}: {}", recoveryCase.getId(), exception.getMessage());
        }
    }

    /**
     * Renders the human-readable recovery plan text from ranked zone recommendations.
     *
     * <p>Produces a numbered "Likely Recovery Zones" list ("1. Label - score%") followed by a
     * "Why:" section listing up to four distinct reasons aggregated across all zones. Falls back to
     * a generic sentence when there are no recommendations.
     *
     * @param recommendations ranked zone recommendations (possibly empty)
     * @return the trimmed plan text
     */
    private String planText(List<RecoveryPlanningService.ZoneRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return "Staff are checking likely locations based on the report details.";
        }
        // Numbered list of zones with their scores.
        StringBuilder builder = new StringBuilder("Likely Recovery Zones\n");
        for (int index = 0; index < recommendations.size(); index++) {
            RecoveryPlanningService.ZoneRecommendation zone = recommendations.get(index);
            builder.append(index + 1).append(". ").append(zone.zoneLabel()).append(" - ").append(zone.score()).append("%\n");
        }
        // Aggregate the distinct supporting reasons across all zones, capped at four bullet points.
        builder.append("\nWhy:\n");
        recommendations.stream()
                .flatMap(zone -> zone.reasons().stream())
                .distinct()
                .limit(4)
                .forEach(reason -> builder.append("- ").append(reason).append("\n"));
        return builder.toString().trim();
    }

    /**
     * Normalizes a lost report's heterogeneous {@code matchedItems} list into typed
     * {@link MatchSuggestion} objects.
     *
     * <p>Each element may already be a {@link MatchSuggestion} (kept as-is) or a raw map (converted,
     * reading the found-item id/title under either camelCase or snake_case keys; entries without an
     * id are dropped). Anything else is ignored. Returns an empty list when there are no matches.
     *
     * @param report the lost report whose matches to interpret
     * @return the typed match suggestions
     */
    private List<MatchSuggestion> typedMatches(LostReport report) {
        if (report.getMatchedItems() == null) {
            return List.of();
        }
        List<MatchSuggestion> suggestions = new ArrayList<>();
        for (Object value : report.getMatchedItems()) {
            switch (value) {
                // Already typed — keep directly.
                case MatchSuggestion suggestion -> suggestions.add(suggestion);
                // Raw map — read id/title tolerating both camelCase and snake_case keys.
                case Map<?, ?> raw -> {
                    MatchSuggestion suggestion = new MatchSuggestion();
                    Object id = raw.containsKey("foundItemId") ? raw.get("foundItemId") : raw.get("found_item_id");
                    Object title = raw.containsKey("foundItemTitle") ? raw.get("foundItemTitle") : raw.get("found_item_title");
                    // Only keep entries that actually carry a found-item id.
                    if (id != null) {
                        suggestion.setFoundItemId(String.valueOf(id));
                        suggestion.setFoundItemTitle(title == null ? "" : String.valueOf(title));
                        suggestions.add(suggestion);
                    }
                }
                // Unrecognized element type — ignore.
                default -> {
                }
            }
        }
        return suggestions;
    }

    /** Generates a 10-character lowercase id fragment from a random UUID (dashes removed). */
    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /**
     * Fills in default values on a freshly-created lost report (id, status "open", urgency
     * "medium", and created/updated timestamps) without overwriting any value already present.
     */
    private void applyLostReportDefaults(LostReport report) {
        String now = clock.now();
        report.setId(valueOrDefault(report.getId(), "lost_" + shortId()));
        report.setStatus(valueOrDefault(report.getStatus(), "open"));
        report.setUrgency(valueOrDefault(report.getUrgency(), "medium"));
        report.setCreatedDate(valueOrDefault(report.getCreatedDate(), now));
        report.setUpdatedDate(valueOrDefault(report.getUpdatedDate(), now));
    }

    /**
     * Validates the required fields on a lost report: a title, a category, and a syntactically valid
     * contact email.
     *
     * @throws BadRequestException if any required field is missing or the email is malformed
     */
    private void validateLostReport(LostReport report) {
        require(report.getTitle(), "Item title is required.");
        require(report.getCategory(), "Category is required.");
        requireEmail(report.getContactEmail(), "Valid contact email is required.");
    }

    /**
     * Rejects a case status that is non-blank yet not part of {@link #CASE_STATUSES}. A blank status
     * is permitted (treated as "no change supplied").
     *
     * @throws BadRequestException if the status is unrecognized
     */
    private void validateCaseStatus(String status) {
        if (!isBlank(status) && !CASE_STATUSES.contains(normalize(status))) {
            throw new BadRequestException("Recovery case status must be one of " + CASE_STATUSES + ".");
        }
    }

    /** Returns {@code value} if non-null and non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Null-safe blank check. */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Lowercases and trims a string for case-insensitive comparison; null becomes empty. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Asserts a value is present (non-blank).
     *
     * @throws BadRequestException with {@code message} if the value is blank
     */
    private void require(String value, String message) {
        if (isBlank(value)) {
            throw new BadRequestException(message);
        }
    }

    /**
     * Asserts a value is a present, syntactically valid email.
     *
     * @throws BadRequestException with {@code message} if blank or not matching {@link #EMAIL_PATTERN}
     */
    private void requireEmail(String value, String message) {
        if (isBlank(value) || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }

    /**
     * Returns the first non-blank value found among the given candidate keys in the map (trimmed),
     * or an empty string if none are present. Used to accept aliased request keys.
     */
    private String stringValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    /**
     * Writes an audit-log entry for a state change, if auditing is enabled.
     *
     * <p>No-op when no audit repository is wired or the actor ({@code performedBy}) is blank.
     * Otherwise persists an {@link AuditLog} capturing the action, target entity, actor, details,
     * and timestamp.
     */
    private void audit(String action, String entityType, String entityId, String performedBy, String details) {
        if (auditLogs == null || isBlank(performedBy)) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setId("audit_" + shortId());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setDetails(details);
        log.setCreatedDate(clock.now());
        auditLogs.save(log);
    }
}
