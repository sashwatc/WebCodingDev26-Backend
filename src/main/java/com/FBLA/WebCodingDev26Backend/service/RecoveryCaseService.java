package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.RecoveryCaseListItem;
import com.FBLA.WebCodingDev26Backend.dto.RecoveryCenterResponse;
import com.FBLA.WebCodingDev26Backend.dto.RecoveryCenterSummary;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecoveryCaseService {
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
    private static final Set<String> MISSION_STATUSES = Set.of(
            "open",
            "assigned",
            "checked",
            "completed",
            "canceled"
    );

    private final RecoveryCaseRepository cases;
    private final RecoveryMissionRepository missions;
    private final LostReportRepository lostReports;
    private final ClaimRepository claims;
    private final AuditLogRepository auditLogs;
    private final RecoveryPlanningService planningService;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final InputSanitizer sanitizer;

    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            RecoveryMissionRepository missions,
            LostReportRepository lostReports,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock
    ) {
        this(cases, missions, lostReports, null, null, planningService, mapper, clock, new InputSanitizer());
    }

    @Autowired
    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            RecoveryMissionRepository missions,
            LostReportRepository lostReports,
            ClaimRepository claims,
            AuditLogRepository auditLogs,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this.cases = cases;
        this.missions = missions;
        this.lostReports = lostReports;
        this.claims = claims;
        this.auditLogs = auditLogs;
        this.planningService = planningService;
        this.mapper = mapper;
        this.clock = clock;
        this.sanitizer = sanitizer == null ? new InputSanitizer() : sanitizer;
    }

    public List<RecoveryCase> list() {
        return cases.findAll();
    }

    public RecoveryCenterResponse center() {
        List<RecoveryCase> allCases = cases.findAll();
        List<RecoveryCaseListItem> caseItems = allCases.stream()
                .map(this::caseListItem)
                .toList();

        long activeCases = allCases.stream().filter(this::isActiveCase).count();
        long openMissions = allCases.stream()
                .map(RecoveryCase::getId)
                .flatMap(caseId -> missions.findByRecoveryCaseId(caseId).stream())
                .filter(this::isOpenMission)
                .count();
        long claimsAwaitingReview = claims == null ? 0 : claims.findAll().stream().filter(this::isClaimAwaitingReview).count();
        long pickupReadyCases = allCases.stream().filter(recoveryCase -> statusEquals(recoveryCase.getStatus(), "pickup_ready")).count();

        return new RecoveryCenterResponse(
                new RecoveryCenterSummary(activeCases, openMissions, claimsAwaitingReview, pickupReadyCases),
                caseItems
        );
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
        upsertMissions(saved, recommendations);
        audit("RECOVERY_PLAN_REFRESHED", "RecoveryCase", saved.getId(), adminEmail,
                "Refreshed deterministic recovery plan for lost report " + report.getId() + ".");
        return saved;
    }

    public RecoveryCase update(String id, Map<String, Object> data) {
        return update(id, data, null);
    }

    public RecoveryCase update(String id, Map<String, Object> data, String adminEmail) {
        RecoveryCase existing = get(id);
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        RecoveryCase patch = mapper.convert(sanitized, RecoveryCase.class);
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "lostReportId", "caseCode");
        validateCaseStatus(existing.getStatus());
        existing.setUpdatedDate(clock.now());
        RecoveryCase saved = cases.save(existing);
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

    public List<RecoveryMission> missionsForCase(String caseId) {
        get(caseId);
        return missions.findByRecoveryCaseId(caseId);
    }

    public RecoveryMission createMission(String caseId, Map<String, Object> data, String adminEmail) {
        RecoveryCase recoveryCase = get(caseId);
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        RecoveryMission mission = mapper.convert(sanitized, RecoveryMission.class);
        String now = clock.now();
        mission.setId(valueOrDefault(mission.getId(), "mission_" + shortId()));
        mission.setRecoveryCaseId(recoveryCase.getId());
        mission.setEventHubId(valueOrDefault(mission.getEventHubId(), recoveryCase.getEventHubId()));
        mission.setCampusZoneId(valueOrDefault(mission.getCampusZoneId(), recoveryCase.getCampusZoneId()));
        mission.setTitle(valueOrDefault(mission.getTitle(), "Review recovery lead"));
        mission.setRecommendedAction(valueOrDefault(mission.getRecommendedAction(), "Staff should review this recovery lead and update the mission status."));
        mission.setStatus(valueOrDefault(mission.getStatus(), "open"));
        validateMissionStatus(mission.getStatus());
        mission.setPriority(valueOrDefault(mission.getPriority(), "medium"));
        mission.setIsDemo(Boolean.TRUE.equals(recoveryCase.getIsDemo()) || Boolean.TRUE.equals(mission.getIsDemo()));
        mission.setCreatedDate(valueOrDefault(mission.getCreatedDate(), now));
        mission.setUpdatedDate(now);
        RecoveryMission saved = missions.save(mission);
        audit("RECOVERY_MISSION_CREATED", "RecoveryMission", saved.getId(), adminEmail,
                "Created mission for recovery case " + recoveryCase.getId() + ".");
        return saved;
    }

    public RecoveryMission updateMission(String id, Map<String, Object> data) {
        return updateMission(id, data, null);
    }

    public RecoveryMission updateMission(String id, Map<String, Object> data, String adminEmail) {
        RecoveryMission existing = missions.findById(id).orElseThrow(() -> new NotFoundException("Recovery mission not found"));
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        RecoveryMission patch = mapper.convert(sanitized, RecoveryMission.class);
        mapper.copyPresent(sanitized, patch, existing, "id", "createdDate", "recoveryCaseId");
        validateMissionStatus(existing.getStatus());
        if (statusEquals(existing.getStatus(), "completed") && isBlank(existing.getCompletedDate())) {
            existing.setCompletedDate(clock.now());
        }
        existing.setUpdatedDate(clock.now());
        RecoveryMission saved = missions.save(existing);
        audit("RECOVERY_MISSION_UPDATED", "RecoveryMission", saved.getId(), adminEmail, "Updated recovery mission.");
        return saved;
    }

    public void onClaimSubmitted(Claim claim) {
        if (isBlank(claim.getFoundItemId())) {
            return;
        }
        cases.findAll().stream()
                .filter(recoveryCase -> claim.getFoundItemId().equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    recoveryCase.setLinkedClaimId(claim.getId());
                    if (!"returned".equals(recoveryCase.getStatus()) && !"closed".equals(recoveryCase.getStatus())) {
                        recoveryCase.setStatus("claim_in_review");
                    }
                    recoveryCase.setUpdatedDate(clock.now());
                    cases.save(recoveryCase);
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
                    recoveryCase.setLinkedClaimId(valueOrDefault(recoveryCase.getLinkedClaimId(), claimId));
                    recoveryCase.setStatus(status);
                    recoveryCase.setUpdatedDate(clock.now());
                    cases.save(recoveryCase);
                });
    }

    private void upsertMissions(RecoveryCase recoveryCase, List<RecoveryPlanningService.ZoneRecommendation> recommendations) {
        Map<String, RecoveryMission> existingByZone = new LinkedHashMap<>();
        for (RecoveryMission existing : missions.findByRecoveryCaseId(recoveryCase.getId())) {
            existingByZone.put(existing.getCampusZoneId(), existing);
        }

        for (RecoveryPlanningService.ZoneRecommendation recommendation : recommendations) {
            RecoveryMission mission = existingByZone.getOrDefault(recommendation.campusZoneId(), new RecoveryMission());
            boolean isNew = mission.getId() == null;
            if (isNew) {
                mission.setId("mission_" + shortId());
                mission.setRecoveryCaseId(recoveryCase.getId());
                mission.setCreatedDate(clock.now());
                mission.setStatus("open");
                mission.setIsDemo(Boolean.TRUE.equals(recoveryCase.getIsDemo()));
            }
            mission.setEventHubId(recoveryCase.getEventHubId());
            mission.setCampusZoneId(recommendation.campusZoneId());
            mission.setZoneLabel(recommendation.zoneLabel());
            mission.setTitle("Check " + recommendation.zoneLabel());
            mission.setRecommendedAction("Staff should check this zone and compare any found item with the lost report.");
            mission.setReasons(recommendation.reasons());
            mission.setScore(recommendation.score());
            mission.setPriority(recommendation.score() >= 70 ? "high" : recommendation.score() >= 45 ? "medium" : "low");
            mission.setUpdatedDate(clock.now());
            missions.save(mission);
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
            if (value instanceof MatchSuggestion suggestion) {
                suggestions.add(suggestion);
            } else if (value instanceof Map<?, ?> raw) {
                MatchSuggestion suggestion = new MatchSuggestion();
                Object id = raw.containsKey("foundItemId") ? raw.get("foundItemId") : raw.get("found_item_id");
                Object title = raw.containsKey("foundItemTitle") ? raw.get("foundItemTitle") : raw.get("found_item_title");
                if (id != null) {
                    suggestion.setFoundItemId(String.valueOf(id));
                    suggestion.setFoundItemTitle(title == null ? "" : String.valueOf(title));
                    suggestions.add(suggestion);
                }
            }
        }
        return suggestions;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private RecoveryCaseListItem caseListItem(RecoveryCase recoveryCase) {
        Optional<LostReport> report = isBlank(recoveryCase.getLostReportId())
                ? Optional.empty()
                : lostReports.findById(recoveryCase.getLostReportId());
        List<RecoveryMission> caseMissions = missions.findByRecoveryCaseId(recoveryCase.getId());
        return new RecoveryCaseListItem(
                recoveryCase,
                report.orElse(null),
                nextAction(recoveryCase, caseMissions),
                updates(recoveryCase, report.orElse(null), caseMissions),
                caseMissions
        );
    }

    private String nextAction(RecoveryCase recoveryCase, List<RecoveryMission> caseMissions) {
        String status = normalize(recoveryCase.getStatus());
        if ("open".equals(status) && caseMissions.isEmpty()) {
            return "Refresh plan and create recovery missions.";
        }
        if ("open".equals(status)) {
            return "Assign staff to open recovery missions.";
        }
        if ("match_identified".equals(status)) {
            return "Review suggested match and invite a claim if appropriate.";
        }
        if ("claim_in_review".equals(status)) {
            return "Review ownership evidence and approve or deny the claim.";
        }
        if ("pickup_ready".equals(status)) {
            return "Coordinate pickup and confirm return.";
        }
        if ("paused".equals(status)) {
            return "Review paused case and reopen or archive it.";
        }
        return "No immediate action required.";
    }

    private List<String> updates(RecoveryCase recoveryCase, LostReport report, List<RecoveryMission> caseMissions) {
        List<String> updates = new ArrayList<>();
        updates.add(report == null ? "Missing linked Lost Report." : "Linked Lost Report: " + report.getId());
        updates.add(caseMissions.size() + " mission(s) on file.");
        if (!isBlank(recoveryCase.getSelectedFoundItemId())) {
            updates.add("Possible found-item match: " + recoveryCase.getSelectedFoundItemId());
        }
        if (!isBlank(recoveryCase.getLinkedClaimId())) {
            updates.add("Linked claim: " + recoveryCase.getLinkedClaimId());
        }
        if (!isBlank(recoveryCase.getUpdatedDate())) {
            updates.add("Last updated " + recoveryCase.getUpdatedDate());
        }
        return updates;
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

    private void validateMissionStatus(String status) {
        if (!isBlank(status) && !MISSION_STATUSES.contains(normalize(status))) {
            throw new BadRequestException("Recovery mission status must be one of " + MISSION_STATUSES + ".");
        }
    }

    private boolean isActiveCase(RecoveryCase recoveryCase) {
        return !List.of("returned", "closed", "archived").contains(normalize(recoveryCase.getStatus()));
    }

    private boolean isOpenMission(RecoveryMission mission) {
        return !List.of("completed", "canceled").contains(normalize(mission.getStatus()));
    }

    private boolean isClaimAwaitingReview(Claim claim) {
        return List.of("", "submitted", "pending_review", "under_review", "need_more_info")
                .contains(normalize(claim.getStatus()));
    }

    private boolean statusEquals(String actual, String expected) {
        return normalize(actual).equals(expected);
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
