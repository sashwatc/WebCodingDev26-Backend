package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LossSentinelService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LossSentinelService.class);
    private static final int MIN_RECENT_REPORTS = 3;
    private static final int MIN_BASELINE_REPORTS = 2;
    private static final Set<String> CLOSED_STATUSES = Set.of("dismissed", "resolved");

    private final PreventionAlertRepository alerts;
    private final LostReportRepository lostReports;
    @SuppressWarnings("unused")
    private final CampusZoneRepository zones;
    private final RecoveryCaseService recoveryCaseService;
    private final AuditLogRepository auditLogs;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final InputSanitizer sanitizer;
    private final RecoveryPulseDispatcher recoveryPulse;

    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(alerts, lostReports, zones, null, null, mapper, clock, new InputSanitizer(), null);
    }

    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            RecoveryCaseService recoveryCaseService,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this(alerts, lostReports, zones, recoveryCaseService, auditLogs, mapper, clock, sanitizer, null);
    }

    @Autowired
    public LossSentinelService(
            PreventionAlertRepository alerts,
            LostReportRepository lostReports,
            CampusZoneRepository zones,
            RecoveryCaseService recoveryCaseService,
            AuditLogRepository auditLogs,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer,
            RecoveryPulseDispatcher recoveryPulse
    ) {
        this.alerts = alerts;
        this.lostReports = lostReports;
        this.zones = zones;
        this.recoveryCaseService = recoveryCaseService;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
        this.clock = clock;
        this.sanitizer = sanitizer == null ? new InputSanitizer() : sanitizer;
        this.recoveryPulse = recoveryPulse;
    }

    public List<PreventionAlert> list() {
        return alerts.findAll();
    }

    public PatternReviewResult recompute() {
        LocalDate today = LocalDate.parse(clock.now().substring(0, 10));
        LocalDate recentStart = today.minusDays(7);
        LocalDate baselineStart = today.minusDays(37);
        LocalDate baselineEnd = recentStart.minusDays(1);
        String calculatedAt = clock.now();

        Map<GroupKey, Counts> grouped = new LinkedHashMap<>();
        Map<AlertKey, PreventionAlert> existingByKey = new LinkedHashMap<>();
        for (PreventionAlert alert : alerts.findAll()) {
            existingByKey.putIfAbsent(alertKey(alert), alert);
        }

        List<LostReport> reports = lostReports.findAll();
        for (LostReport report : reports) {
            LocalDate date = parseDate(report.getDateLost());
            if (date == null || report.getCategory() == null || report.getCategory().isBlank()) {
                continue;
            }
            GroupKey key = new GroupKey(valueOrDefault(report.getCampusZoneId(), "unknown"), report.getCategory());
            Counts counts = grouped.computeIfAbsent(key, ignored -> new Counts());
            if (!date.isBefore(recentStart) && !date.isAfter(today)) {
                counts.recentReportIds.add(report.getId());
                counts.observedDemo = counts.observedDemo && Boolean.TRUE.equals(report.getIsDemo());
            } else if (!date.isBefore(baselineStart) && date.isBefore(recentStart)) {
                counts.baselineReportIds.add(report.getId());
                counts.baselineDemo = counts.baselineDemo && Boolean.TRUE.equals(report.getIsDemo());
            }
        }

        List<PreventionAlert> savedAlerts = new ArrayList<>();
        for (Map.Entry<GroupKey, Counts> entry : grouped.entrySet()) {
            Counts counts = entry.getValue();
            int observed = counts.recentReportIds.size();
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
            if (alert != null && isResolved(alert.getStatus())) {
                continue;
            }
            if (alert == null) {
                alert = new PreventionAlert();
                alert.setId("alert_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
                alert.setCreatedDate(calculatedAt);
            }
            alert.setTenantId("pvhs");
            alert.setTitle("Pattern Review powered by Loss Sentinel");
            alert.setAlertType("volume_spike");
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
            alert.setStatus(valueOrDefault(alert.getStatus(), "open"));
            alert.setCalculatedAt(calculatedAt);
            alert.setIsDemo(counts.observedDemo && counts.baselineDemo);
            PreventionAlert saved = alerts.save(alert);
            savedAlerts.add(saved);
            if (isNewAlert) {
                notifyPatternReviewAlert(saved);
            }
        }

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

    public PreventionAlert update(String id, Map<String, Object> data, String adminEmail) {
        PreventionAlert existing = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        PreventionAlert patch = mapper.convert(sanitized, PreventionAlert.class);
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "sourceLostReportIds", "observedCount", "baselineCount", "calculatedAt");
        if (isResolved(existing.getStatus()) && (existing.getResolvedDate() == null || existing.getResolvedDate().isBlank())) {
            existing.setResolvedDate(clock.now());
            existing.setResolvedBy(adminEmail);
        }
        PreventionAlert saved = alerts.save(existing);
        audit("PATTERN_REVIEW_ALERT_UPDATED", "PreventionAlert", saved.getId(), adminEmail, "Updated Pattern Review alert.");
        return saved;
    }

    public PreventionAlert acknowledge(String id, String adminEmail) {
        return transition(id, "acknowledged", adminEmail, "Acknowledged Pattern Review alert.");
    }

    public PreventionAlert dismiss(String id, String adminEmail, Map<String, Object> data) {
        return transition(id, "dismissed", adminEmail, noteFrom(data, "Dismissed Pattern Review alert."));
    }

    public PreventionAlert resolve(String id, String adminEmail, Map<String, Object> data) {
        return transition(id, "resolved", adminEmail, noteFrom(data, "Resolved Pattern Review alert."));
    }

    public List<LostReport> sourceReports(String id) {
        PreventionAlert alert = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        if (alert.getSourceLostReportIds().isEmpty()) {
            return List.of();
        }
        return alert.getSourceLostReportIds().stream()
                .map(lostReports::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    private PreventionAlert transition(String id, String status, String adminEmail, String note) {
        PreventionAlert existing = alerts.findById(id).orElseThrow(() -> new NotFoundException("Prevention alert not found"));
        existing.setStatus(status);
        if (isResolved(status)) {
            existing.setResolvedDate(clock.now());
            existing.setResolvedBy(adminEmail);
            existing.setResolutionNotes(note);
        }
        PreventionAlert saved = alerts.save(existing);
        audit("PATTERN_REVIEW_ALERT_" + status.toUpperCase(), "PreventionAlert", saved.getId(), adminEmail, note);
        return saved;
    }

    private LostReport firstSourceReport(PreventionAlert alert) {
        for (String lostReportId : alert.getSourceLostReportIds()) {
            Optional<LostReport> report = lostReports.findById(lostReportId);
            if (report.isPresent()) {
                return report.get();
            }
        }
        throw new BadRequestException("Pattern Review alert does not have source Lost Reports.");
    }

    private boolean isResolved(String status) {
        return status != null && CLOSED_STATUSES.contains(status.trim().toLowerCase());
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

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

    private String noteFrom(Map<String, Object> data, String fallback) {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        Object value = sanitized.getOrDefault("resolution_notes", sanitized.getOrDefault("resolutionNotes", sanitized.get("admin_notes")));
        String note = value == null ? "" : String.valueOf(value).trim();
        return note.isBlank() ? fallback : note;
    }

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

    private record GroupKey(String campusZoneId, String category) {
    }

    private record AlertKey(String tenantId, String alertType, String campusZoneId, String category, String timeWindowStart, String timeWindowEnd) {
    }

    private static class Counts {
        private final List<String> recentReportIds = new ArrayList<>();
        private final List<String> baselineReportIds = new ArrayList<>();
        private boolean observedDemo = true;
        private boolean baselineDemo = true;
    }
}
