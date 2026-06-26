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

@Service
public class RecoveryCaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryCaseService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
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

    private final RecoveryCaseRepository cases;
    private final LostReportRepository lostReports;
    private final AuditLogRepository auditLogs;
    private final RecoveryPlanningService planningService;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final InputSanitizer sanitizer;
    private final RecoveryPulseDispatcher recoveryPulse;

    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            LostReportRepository lostReports,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(cases, lostReports, null, planningService, mapper, clock, new InputSanitizer(), null);
    }

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

    public List<RecoveryCase> list() {
        return cases.findAll();
    }

    public RecoveryCase get(String id) {
        return cases.findById(id).orElseThrow(() -> new NotFoundException("Recovery case not found"));
    }

    public RecoveryCase getByLostReport(String lostReportId) {
        return cases.findByLostReportId(lostReportId)
                .orElseGet(() -> ensureForLostReport(lostReports.findById(lostReportId)
                        .orElseThrow(() -> new NotFoundException("Lost report not found"))));
    }

    public RecoveryCase createFromLostReport(String lostReportId, String adminEmail) {
        LostReport report = lostReports.findById(lostReportId).orElseThrow(() -> new NotFoundException("Lost report not found"));
        RecoveryCase recoveryCase = ensureForLostReport(report);
        audit("RECOVERY_CASE_CREATED", "RecoveryCase", recoveryCase.getId(), adminEmail,
                "Created or confirmed recovery case for lost report " + report.getId() + ".");
        return recoveryCase;
    }

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

    public RecoveryCase ensureForLostReport(LostReport report) {
        if (report == null || isBlank(report.getId())) {
            throw new BadRequestException("Recovery cases must reference a saved lost report.");
        }
        return cases.findByLostReportId(report.getId()).orElseGet(() -> {
            String now = clock.now();
            RecoveryCase recoveryCase = new RecoveryCase();
            recoveryCase.setId("case_" + shortId());
            recoveryCase.setCaseCode("PVHS-RM-" + Instant.parse(now).toString().substring(0, 10).replace("-", "") + "-" + shortId().substring(0, 4).toUpperCase());
            recoveryCase.setTenantId("pvhs");
            recoveryCase.setLostReportId(report.getId());
            recoveryCase.setEventHubId(report.getEventHubId());
            recoveryCase.setCampusZoneId(report.getCampusZoneId());
            recoveryCase.setStatus("open");
            recoveryCase.setPriority(valueOrDefault(report.getUrgency(), "medium"));
            recoveryCase.setSummary(valueOrDefault(report.getTitle(), "Lost item recovery case"));
            recoveryCase.setRecoveryPlan("Staff are checking likely locations.");
            recoveryCase.setIsDemo(Boolean.TRUE.equals(report.getIsDemo()));
            recoveryCase.setCreatedDate(now);
            recoveryCase.setUpdatedDate(now);
            return cases.save(recoveryCase);
        });
    }

    public RecoveryCase refreshForLostReport(String lostReportId) {
        return refreshForLostReport(lostReportId, null);
    }

    public RecoveryCase refreshForLostReport(String lostReportId, String adminEmail) {
        LostReport report = lostReports.findById(lostReportId).orElseThrow(() -> new NotFoundException("Lost report not found"));
        RecoveryCase recoveryCase = ensureForLostReport(report);
        String previousStatus = recoveryCase.getStatus();
        List<RecoveryPlanningService.ZoneRecommendation> recommendations = planningService.recommendZones(report);
        recoveryCase.setEventHubId(report.getEventHubId());
        recoveryCase.setCampusZoneId(report.getCampusZoneId());
        recoveryCase.setLikelyZoneSummaries(recommendations.stream()
                .map(zone -> zone.zoneLabel() + " - " + zone.score() + "%")
                .toList());
        recoveryCase.setRecoveryPlan(planText(recommendations));

        List<MatchSuggestion> matches = typedMatches(report);
        if (!matches.isEmpty()) {
            recoveryCase.setSelectedFoundItemId(matches.get(0).getFoundItemId());
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

    public RecoveryCase update(String id, Map<String, Object> data) {
        return update(id, data, null);
    }

    public RecoveryCase update(String id, Map<String, Object> data, String adminEmail) {
        RecoveryCase existing = get(id);
        String previousStatus = existing.getStatus();
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        RecoveryCase patch = mapper.convert(sanitized, RecoveryCase.class);
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "lostReportId", "caseCode");
        validateCaseStatus(existing.getStatus());
        existing.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(existing);
        notifyCaseStatusChanged(saved, previousStatus);
        audit("RECOVERY_CASE_UPDATED", "RecoveryCase", saved.getId(), adminEmail, "Updated recovery case fields.");
        return saved;
    }

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

    public void onClaimSubmitted(Claim claim) {
        if (isBlank(claim.getFoundItemId())) {
            return;
        }
        cases.findAll().stream()
                .filter(recoveryCase -> claim.getFoundItemId().equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    String previousStatus = recoveryCase.getStatus();
                    recoveryCase.setLinkedClaimId(claim.getId());
                    if (!"returned".equals(recoveryCase.getStatus()) && !"closed".equals(recoveryCase.getStatus())) {
                        recoveryCase.setStatus("claim_in_review");
                    }
                    recoveryCase.setUpdatedDate(clock.now());
                    RecoveryCase saved = cases.save(recoveryCase);
                    notifyCaseStatusChanged(saved, previousStatus);
                });
    }

    public void markPickupReady(String claimId, String foundItemId) {
        updateCaseByClaimOrItem(claimId, foundItemId, "pickup_ready");
    }

    public void markReturned(String claimId, String foundItemId) {
        updateCaseByClaimOrItem(claimId, foundItemId, "returned");
    }

    private void updateCaseByClaimOrItem(String claimId, String foundItemId, String status) {
        cases.findAll().stream()
                .filter(recoveryCase -> claimId.equals(recoveryCase.getLinkedClaimId()) || foundItemId.equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    String previousStatus = recoveryCase.getStatus();
                    recoveryCase.setLinkedClaimId(valueOrDefault(recoveryCase.getLinkedClaimId(), claimId));
                    recoveryCase.setStatus(status);
                    recoveryCase.setUpdatedDate(clock.now());
                    RecoveryCase saved = cases.save(recoveryCase);
                    notifyCaseStatusChanged(saved, previousStatus);
                });
    }

    private void notifyCaseStatusChanged(RecoveryCase recoveryCase, String previousStatus) {
        if (recoveryPulse == null || normalize(recoveryCase.getStatus()).equals(normalize(previousStatus))) {
            return;
        }
        try {
            recoveryPulse.recoveryCaseStatusUpdated(recoveryCase, previousStatus);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to dispatch recovery case notification for case {}: {}", recoveryCase.getId(), exception.getMessage());
        }
    }

    private String planText(List<RecoveryPlanningService.ZoneRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return "Staff are checking likely locations based on the report details.";
        }
        StringBuilder builder = new StringBuilder("Likely Recovery Zones\n");
        for (int index = 0; index < recommendations.size(); index++) {
            RecoveryPlanningService.ZoneRecommendation zone = recommendations.get(index);
            builder.append(index + 1).append(". ").append(zone.zoneLabel()).append(" - ").append(zone.score()).append("%\n");
        }
        builder.append("\nWhy:\n");
        recommendations.stream()
                .flatMap(zone -> zone.reasons().stream())
                .distinct()
                .limit(4)
                .forEach(reason -> builder.append("- ").append(reason).append("\n"));
        return builder.toString().trim();
    }

    private List<MatchSuggestion> typedMatches(LostReport report) {
        if (report.getMatchedItems() == null) {
            return List.of();
        }
        List<MatchSuggestion> suggestions = new ArrayList<>();
        for (Object value : report.getMatchedItems()) {
            switch (value) {
                case MatchSuggestion suggestion -> suggestions.add(suggestion);
                case Map<?, ?> raw -> {
                    MatchSuggestion suggestion = new MatchSuggestion();
                    Object id = raw.containsKey("foundItemId") ? raw.get("foundItemId") : raw.get("found_item_id");
                    Object title = raw.containsKey("foundItemTitle") ? raw.get("foundItemTitle") : raw.get("found_item_title");
                    if (id != null) {
                        suggestion.setFoundItemId(String.valueOf(id));
                        suggestion.setFoundItemTitle(title == null ? "" : String.valueOf(title));
                        suggestions.add(suggestion);
                    }
                }
                default -> {
                }
            }
        }
        return suggestions;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private void applyLostReportDefaults(LostReport report) {
        String now = clock.now();
        report.setId(valueOrDefault(report.getId(), "lost_" + shortId()));
        report.setStatus(valueOrDefault(report.getStatus(), "open"));
        report.setUrgency(valueOrDefault(report.getUrgency(), "medium"));
        report.setCreatedDate(valueOrDefault(report.getCreatedDate(), now));
        report.setUpdatedDate(valueOrDefault(report.getUpdatedDate(), now));
    }

    private void validateLostReport(LostReport report) {
        require(report.getTitle(), "Item title is required.");
        require(report.getCategory(), "Category is required.");
        requireEmail(report.getContactEmail(), "Valid contact email is required.");
    }

    private void validateCaseStatus(String status) {
        if (!isBlank(status) && !CASE_STATUSES.contains(normalize(status))) {
            throw new BadRequestException("Recovery case status must be one of " + CASE_STATUSES + ".");
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void require(String value, String message) {
        if (isBlank(value)) {
            throw new BadRequestException(message);
        }
    }

    private void requireEmail(String value, String message) {
        if (isBlank(value) || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }

    private String stringValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

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
