package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecoveryCaseService {
    private final RecoveryCaseRepository cases;
    private final RecoveryMissionRepository missions;
    private final LostReportRepository lostReports;
    private final RecoveryPlanningService planningService;
    private final PatchMapper mapper;
    private final ClockService clock;

    public RecoveryCaseService(
            RecoveryCaseRepository cases,
            RecoveryMissionRepository missions,
            LostReportRepository lostReports,
            RecoveryPlanningService planningService,
            PatchMapper mapper,
            ClockService clock
    ) {
        this.cases = cases;
        this.missions = missions;
        this.lostReports = lostReports;
        this.planningService = planningService;
        this.mapper = mapper;
        this.clock = clock;
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

    public RecoveryCase ensureForLostReport(LostReport report) {
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
            recoveryCase.setCreatedDate(now);
            recoveryCase.setUpdatedDate(now);
            return cases.save(recoveryCase);
        });
    }

    public RecoveryCase refreshForLostReport(String lostReportId) {
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
        return saved;
    }

    public RecoveryCase update(String id, Map<String, Object> data) {
        RecoveryCase existing = get(id);
        RecoveryCase patch = mapper.convert(data, RecoveryCase.class);
        mapper.copyPresent(data, patch, existing, "id", "createdDate", "lostReportId", "caseCode");
        existing.setUpdatedDate(clock.now());
        return cases.save(existing);
    }

    public List<RecoveryMission> missionsForCase(String caseId) {
        get(caseId);
        return missions.findByRecoveryCaseId(caseId);
    }

    public RecoveryMission updateMission(String id, Map<String, Object> data) {
        RecoveryMission existing = missions.findById(id).orElseThrow(() -> new NotFoundException("Recovery mission not found"));
        RecoveryMission patch = mapper.convert(data, RecoveryMission.class);
        mapper.copyPresent(data, patch, existing, "id", "createdDate", "recoveryCaseId");
        if ("completed".equals(existing.getStatus()) && isBlank(existing.getCompletedDate())) {
            existing.setCompletedDate(clock.now());
        }
        existing.setUpdatedDate(clock.now());
        return missions.save(existing);
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

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
