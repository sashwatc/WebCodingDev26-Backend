package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * "Loss Sentinel" — the engine behind the admin "Pattern Review" feature.
 *
 * <p>Responsibility: scan real Lost Reports and detect statistically meaningful spikes in
 * losses within a given campus zone + item category, then create/update {@link PreventionAlert}
 * records so staff can act proactively (e.g. post a reminder, open a recovery mission).
 *
 * <p>Core business logic (volume-spike detection): compares a 7-day "recent" window against a
 * trailing 30-day "baseline" window, normalizes the baseline to a comparable 7-day rate, and
 * raises an alert only when conservative thresholds are met (enough recent reports, enough
 * baseline history, and a >=2x increase). These plain-count rules intentionally avoid showing
 * fabricated alerts on thin/demo data. It also owns the alert lifecycle (acknowledge, dismiss,
 * resolve, update) with audit logging and optional owner/staff notifications.
 *
 * <p>Collaborators: {@link PreventionAlertRepository} and {@link LostReportRepository} (data),
 * {@link AuditLogRepository} (audit trail), {@link PatchMapper} (partial updates),
 * {@link ClockService} (timestamps), {@link InputSanitizer} (cleansing admin notes), and
 * {@link RecoveryPulseDispatcher} (notifications). {@link CampusZoneRepository} is injected for
 * future use but not currently read.
 */
@Service
public class LossSentinelService {
    /** Logger used for non-fatal notification dispatch failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LossSentinelService.class);
    /** Minimum number of recent-window Lost Reports required before an alert can fire. */
    private static final int MIN_RECENT_REPORTS = 3;
    /** Minimum number of baseline-window Lost Reports required to provide real comparison history. */
    private static final int MIN_BASELINE_REPORTS = 2;
    /** Alert statuses considered closed; closed alerts are not revived by a recompute. */
    private static final Set<String> CLOSED_STATUSES = Set.of("dismissed", "resolved");

    /** Repository for prevention/pattern-review alerts (read existing, persist new/updated). */
    private final PreventionAlertRepository alerts;
    /** Source of truth for Lost Reports analyzed during pattern detection. */
    private final LostReportRepository lostReports;
    /** Campus zone repository; injected for future enrichment, currently unused. */
    @SuppressWarnings("unused")
    private final CampusZoneRepository zones;
    /** Audit log repository; records admin lifecycle actions (may be null in lightweight wiring). */
    private final AuditLogRepository auditLogs;
    /** Maps/copies sanitized fields onto existing alerts for partial updates. */
    private final PatchMapper mapper;
    /** Clock abstraction supplying the current ISO timestamp / date string. */
    private final ClockService clock;
    /** Sanitizes admin-supplied data (notes) before use. */
    private final InputSanitizer sanitizer;
    /** Dispatches notifications when a new pattern-review alert is created (may be null). */
    private final RecoveryPulseDispatcher recoveryPulse;

    /**
     * Minimal constructor (tests / lightweight wiring): no audit log, no notifications, default sanitizer.
     */
    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(alerts, lostReports, zones, null, mapper, clock, new InputSanitizer(), null);
    }

    /**
     * Intermediate constructor: includes audit logging and a custom sanitizer but no notifications.
     */
    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this(alerts, lostReports, zones, auditLogs, mapper, clock, sanitizer, null);
    }

    /**
     * Primary (Spring-injected) constructor wiring all collaborators.
     *
     * @param recoveryPulse optional notification dispatcher; falls back to a fresh
     *                      {@link InputSanitizer} when {@code sanitizer} is null
     */
    @Autowired
    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer,
            RecoveryPulseDispatcher recoveryPulse
    ) {
        this.alerts = alerts;
        this.lostReports = lostReports;
        this.zones = zones;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
        this.clock = clock;
        this.sanitizer = sanitizer == null ? new InputSanitizer() : sanitizer;
        this.recoveryPulse = recoveryPulse;
    }

    /**
     * @return all prevention/pattern-review alerts currently stored
     */
    public List<PreventionAlert> list() {
        return alerts.findAll();
    }

    /**
     * Recomputes volume-spike pattern alerts from the current set of Lost Reports.
     *
     * <p>Windows: "recent" = last 7 days (inclusive of today); "baseline" = the 30 days before
     * that (days -37..-8). Reports are bucketed by (campus zone, category); each bucket counts
     * how many fall in each window. The baseline count is normalized to a 7-day-equivalent by
     * dividing by 4 and rounding up (minimum 1). A bucket becomes an alert only when ALL hold:
     * recent >= {@code MIN_RECENT_REPORTS}, baseline reports >= {@code MIN_BASELINE_REPORTS}, and
     * recent >= 2x the normalized baseline. Severity is "high" at >=3x, else "medium". Existing
     * open alerts for the same key are updated in place; closed (dismissed/resolved) alerts are
     * left untouched. Newly created alerts trigger a notification.
     *
     * <p>An alert is flagged as demo only when every contributing report (recent and baseline) is
     * itself a demo report, so demo and real data never mix. Source report ids and human-readable
     * reasons/suggested actions are attached for the admin UI.
     *
     * @return a {@link PatternReviewResult} describing whether alerts were created/updated or the
     *         data was insufficient, along with the windows used and the calculation timestamp
     * @side-effect persists created/updated alerts and may dispatch notifications
     */
    public PatternReviewResult recompute() {
        // Derive the date windows from "today" (date portion of the clock).
        LocalDate today = LocalDate.parse(clock.now().substring(0, 10));
        LocalDate recentStart = today.minusDays(7);      // start of the 7-day recent window
        LocalDate baselineStart = today.minusDays(37);   // start of the 30-day baseline window
        LocalDate baselineEnd = recentStart.minusDays(1);// baseline ends the day before the recent window
        String calculatedAt = clock.now();

        // Accumulators: per zone/category counts, and an index of existing alerts by identity key.
        Map<GroupKey, Counts> grouped = new LinkedHashMap<>();
        Map<AlertKey, PreventionAlert> existingByKey = new LinkedHashMap<>();
        for (PreventionAlert alert : alerts.findAll()) {
            existingByKey.putIfAbsent(alertKey(alert), alert);
        }

        // Bucket every dated, categorized report into its recent or baseline window.
        List<LostReport> reports = lostReports.findAll();
        for (LostReport report : reports) {
            LocalDate date = parseDate(report.getDateLost());
            // Skip reports without a parseable date or a category — they can't be grouped.
            if (date == null || report.getCategory() == null || report.getCategory().isBlank()) {
                continue;
            }
            GroupKey key = new GroupKey(valueOrDefault(report.getCampusZoneId(), "unknown"), report.getCategory());
            Counts counts = grouped.computeIfAbsent(key, ignored -> new Counts());
            if (!date.isBefore(recentStart) && !date.isAfter(today)) {
                // Falls in the recent window.
                counts.recentReportIds.add(report.getId());
                // Bucket stays "demo" only while every recent report is a demo report.
                counts.observedDemo = counts.observedDemo && Boolean.TRUE.equals(report.getIsDemo());
            } else if (!date.isBefore(baselineStart) && date.isBefore(recentStart)) {
                // Falls in the baseline window.
                counts.baselineReportIds.add(report.getId());
                counts.baselineDemo = counts.baselineDemo && Boolean.TRUE.equals(report.getIsDemo());
            }
        }

        // Evaluate each zone/category bucket against the spike thresholds.
        List<PreventionAlert> savedAlerts = new ArrayList<>();
        for (Map.Entry<GroupKey, Counts> entry : grouped.entrySet()) {
            Counts counts = entry.getValue();
            int observed = counts.recentReportIds.size();
            // Normalize the 30-day baseline to a 7-day-equivalent rate (ceil, floor of 1).
            int baseline = Math.max(1, (int) Math.ceil(counts.baselineReportIds.size() / 4.0));

            // Pattern Review intentionally uses plain count thresholds:
            // 1. at least three recent Lost Reports in the same zone/category,
            // 2. at least two older baseline Lost Reports for real history, and
            // 3. recent volume at least 2x the normalized baseline window.
            // This prevents judge demos from showing fabricated alerts on thin data.
            if (observed < MIN_RECENT_REPORTS
                    || counts.baselineReportIds.size() < MIN_BASELINE_REPORTS
                    || observed < baseline * 2) {
                continue;
            }

            // Identity of this alert: tenant + type + zone + category + recent-window bounds.
            AlertKey key = new AlertKey(
                    "pvhs",
                    "volume_spike",
                    entry.getKey().campusZoneId(),
                    entry.getKey().category(),
                    recentStart.toString(),
                    today.toString()
            );
            PreventionAlert alert = existingByKey.get(key);
            boolean isNewAlert = alert == null;
            // Respect a prior admin decision: don't reopen a dismissed/resolved alert.
            if (alert != null && isResolved(alert.getStatus())) {
                continue;
            }
            // Create a fresh alert record when none exists for this key yet.
            if (alert == null) {
                alert = new PreventionAlert();
                alert.setId("alert_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
                alert.setCreatedDate(calculatedAt);
            }
            // Populate/refresh all alert fields from the latest computation.
            alert.setTenantId("pvhs");
            alert.setTitle("Pattern Review powered by Loss Sentinel");
            alert.setAlertType("volume_spike");
            // Severity escalates to "high" at a 3x spike, otherwise "medium".
            alert.setSeverity(observed >= baseline * 3 ? "high" : "medium");
            alert.setCampusZoneId(entry.getKey().campusZoneId());
            alert.setCategory(entry.getKey().category());
            alert.setTimeWindowStart(recentStart.toString());
            alert.setTimeWindowEnd(today.toString());
            alert.setBaselineWindowStart(baselineStart.toString());
            alert.setBaselineWindowEnd(baselineEnd.toString());
            alert.setBaselineCount(baseline);
            alert.setObservedCount(observed);
            alert.setSourceLostReportIds(counts.recentReportIds);
            alert.setReasons(List.of(
                    observed + " " + entry.getKey().category() + " Lost Reports in the recent window.",
                    counts.baselineReportIds.size() + " baseline Lost Reports support comparison history.",
                    "Recent volume is at least 2x the normalized baseline."
            ));
            alert.setSuggestedActions(List.of(
                    "View the source Lost Reports before taking action.",
                    "Create a recovery mission for the affected zone.",
                    "Post a reminder near the zone exit or intake desk."
            ));
            // Preserve any existing status; default new alerts to "open".
            alert.setStatus(valueOrDefault(alert.getStatus(), "open"));
            alert.setCalculatedAt(calculatedAt);
            // Demo only if both windows were entirely demo data.
            alert.setIsDemo(counts.observedDemo && counts.baselineDemo);
            PreventionAlert saved = alerts.save(alert);
            savedAlerts.add(saved);
            // Notify staff only for genuinely new alerts (avoid re-notifying on refresh).
            if (isNewAlert) {
                notifyPatternReviewAlert(saved);
            }
        }

        // No bucket cleared the thresholds -> report insufficient data with the windows examined.
        if (savedAlerts.isEmpty()) {
            return new PatternReviewResult(
                    "not_enough_data",
                    "Pattern Review needs at least 3 recent Lost Reports, 2 baseline Lost Reports, and a 2x baseline increase before creating an alert.",
                    List.of(),
                    reports.size(),
                    recentStart.toString(),
                    today.toString(),
                    baselineStart.toString(),
                    baselineEnd.toString(),
                    calculatedAt
            );
        }

        // One or more alerts were created/updated from real data.
        return new PatternReviewResult(
                "alerts_created",
                savedAlerts.size() + " Pattern Review alert(s) created or updated from real Lost Reports.",
                savedAlerts,
                reports.size(),
                recentStart.toString(),
                today.toString(),
                baselineStart.toString(),
                baselineEnd.toString(),
                calculatedAt
        );
    }

    /**
     * Applies a sanitized partial update to an existing alert (admin edit).
     *
     * <p>Immutable/derived fields are protected from overwrite: id, createdDate, source report
     * ids, observed/baseline counts, and calculatedAt. If the resulting status is closed and no
     * resolution timestamp is set yet, records who resolved it and when. Writes an audit entry.
     *
     * @param id         alert id
     * @param data       raw patch fields from the admin
     * @param adminEmail acting admin (for resolution attribution + audit)
     * @return the saved alert
     * @throws NotFoundException if no alert has the given id
     */
    public PreventionAlert update(String id, Map<String, Object> data, String adminEmail) {
        PreventionAlert existing = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        PreventionAlert patch = mapper.convert(sanitized, PreventionAlert.class);
        // Copy only provided fields onto the existing alert, never the protected/derived ones.
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "sourceLostReportIds", "observedCount", "baselineCount", "calculatedAt");
        // If the edit closed the alert, stamp resolution metadata once.
        if (isResolved(existing.getStatus()) && (existing.getResolvedDate() == null || existing.getResolvedDate().isBlank())) {
            existing.setResolvedDate(clock.now());
            existing.setResolvedBy(adminEmail);
        }
        PreventionAlert saved = alerts.save(existing);
        audit("PATTERN_REVIEW_ALERT_UPDATED", "PreventionAlert", saved.getId(), adminEmail, "Updated Pattern Review alert.");
        return saved;
    }

    /**
     * Transitions an alert to {@code acknowledged} (staff has seen it).
     *
     * @param id alert id; @param adminEmail acting admin
     * @return the saved alert
     * @throws NotFoundException if not found
     */
    public PreventionAlert acknowledge(String id, String adminEmail) {
        return transition(id, "acknowledged", adminEmail, "Acknowledged Pattern Review alert.");
    }

    /**
     * Transitions an alert to {@code dismissed} (closed without action), with an optional note.
     *
     * @param id alert id; @param adminEmail acting admin; @param data may carry resolution notes
     * @return the saved alert
     * @throws NotFoundException if not found
     */
    public PreventionAlert dismiss(String id, String adminEmail, Map<String, Object> data) {
        return transition(id, "dismissed", adminEmail, noteFrom(data, "Dismissed Pattern Review alert."));
    }

    /**
     * Transitions an alert to {@code resolved} (acted upon and closed), with an optional note.
     *
     * @param id alert id; @param adminEmail acting admin; @param data may carry resolution notes
     * @return the saved alert
     * @throws NotFoundException if not found
     */
    public PreventionAlert resolve(String id, String adminEmail, Map<String, Object> data) {
        return transition(id, "resolved", adminEmail, noteFrom(data, "Resolved Pattern Review alert."));
    }

    /**
     * Loads the Lost Reports that contributed to an alert (its evidence trail).
     *
     * @param id alert id
     * @return the source reports, skipping any ids that no longer resolve; empty when none recorded
     * @throws NotFoundException if the alert does not exist
     */
    public List<LostReport> sourceReports(String id) {
        PreventionAlert alert = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        if (alert.getSourceLostReportIds().isEmpty()) {
            return List.of();
        }
        // Resolve each stored id, dropping any that have since been deleted.
        return alert.getSourceLostReportIds().stream()
                .map(lostReportId -> lostReports.findById(lostReportId))
                .flatMap(optionalReport -> optionalReport.stream())
                .toList();
    }

    /**
     * Shared lifecycle-transition helper: sets the status, stamps resolution metadata for closed
     * states, persists, and writes an audit entry keyed by the new status.
     *
     * @param id         alert id
     * @param status     target status (e.g. acknowledged/dismissed/resolved)
     * @param adminEmail acting admin
     * @param note       resolution/audit note
     * @return the saved alert
     * @throws NotFoundException if not found
     */
    private PreventionAlert transition(String id, String status, String adminEmail, String note) {
        PreventionAlert existing = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        existing.setStatus(status);
        // Closing states capture who/when/why.
        if (isResolved(status)) {
            existing.setResolvedDate(clock.now());
            existing.setResolvedBy(adminEmail);
            existing.setResolutionNotes(note);
        }
        PreventionAlert saved = alerts.save(existing);
        audit("PATTERN_REVIEW_ALERT_" + status.toUpperCase(), "PreventionAlert", saved.getId(), adminEmail, note);
        return saved;
    }

    /** @return true when the status is one of the closed states (dismissed/resolved), case-insensitively. */
    private boolean isResolved(String status) {
        return status != null && CLOSED_STATUSES.contains(status.trim().toLowerCase());
    }

    /** @return the ISO date parsed from {@code value}, or {@code null} when it cannot be parsed. */
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** @return {@code value} when non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Builds the stable identity key for an existing alert (used to match it against a recomputed
     * bucket), applying defaults for any missing fields.
     */
    private AlertKey alertKey(PreventionAlert alert) {
        return new AlertKey(
                valueOrDefault(alert.getTenantId(), "pvhs"),
                valueOrDefault(alert.getAlertType(), "volume_spike"),
                valueOrDefault(alert.getCampusZoneId(), "unknown"),
                valueOrDefault(alert.getCategory(), ""),
                valueOrDefault(alert.getTimeWindowStart(), ""),
                valueOrDefault(alert.getTimeWindowEnd(), "")
        );
    }

    /**
     * Extracts an admin-supplied resolution note from a (sanitized) request body.
     *
     * <p>Accepts {@code resolution_notes}, {@code resolutionNotes}, or {@code admin_notes}
     * (in that precedence). Returns {@code fallback} when none is present or blank.
     *
     * @param data     raw request data
     * @param fallback default note text
     * @return the chosen note
     */
    private String noteFrom(Map<String, Object> data, String fallback) {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        Object value = sanitized.getOrDefault("resolution_notes", sanitized.getOrDefault("resolutionNotes", sanitized.get("admin_notes")));
        String note = value == null ? "" : String.valueOf(value).trim();
        return note.isBlank() ? fallback : note;
    }

    /**
     * Writes an audit-log entry for an admin action, when auditing is wired and an actor is known.
     *
     * <p>No-op when the audit repository is absent or {@code performedBy} is blank.
     *
     * @param action      action code (e.g. PATTERN_REVIEW_ALERT_RESOLVED)
     * @param entityType  affected entity type
     * @param entityId    affected entity id
     * @param performedBy acting user email
     * @param details     human-readable detail
     */
    private void audit(String action, String entityType, String entityId, String performedBy, String details) {
        if (auditLogs == null || performedBy == null || performedBy.isBlank()) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setId("audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setDetails(details);
        log.setCreatedDate(clock.now());
        auditLogs.save(log);
    }

    /**
     * Best-effort notification when a new pattern-review alert is created.
     *
     * <p>No-op when no dispatcher is wired; swallows and logs dispatch failures so alert
     * creation is never rolled back by a notification error.
     *
     * @param alert the newly created alert
     */
    private void notifyPatternReviewAlert(PreventionAlert alert) {
        if (recoveryPulse == null) {
            return;
        }
        try {
            recoveryPulse.patternReviewAlert(alert);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to dispatch Pattern Review alert notification {}: {}", alert.getId(), exception.getMessage());
        }
    }

    /** Grouping key for bucketing reports by campus zone + item category. */
    private record GroupKey(String campusZoneId, String category) {
    }

    /** Stable identity of an alert: tenant, type, zone, category, and the recent-window bounds. */
    private record AlertKey(String tenantId, String alertType, String campusZoneId, String category, String timeWindowStart, String timeWindowEnd) {
    }

    /**
     * Mutable per-bucket accumulator: the recent/baseline report ids and whether each window has
     * so far been composed entirely of demo reports (used to flag the alert as demo-only).
     */
    private static class Counts {
        private final List<String> recentReportIds = new ArrayList<>();
        private final List<String> baselineReportIds = new ArrayList<>();
        private boolean observedDemo = true;
        private boolean baselineDemo = true;
    }
}
