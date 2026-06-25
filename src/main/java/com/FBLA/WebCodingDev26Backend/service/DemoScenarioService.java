package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.DemoScenarioResponse;
import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassResponse;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DemoScenarioService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LostReportRepository lostReports;
    private final FoundItemRepository foundItems;
    private final ClaimRepository claims;
    private final AuditLogRepository auditLogs;
    private final RecoveryCaseService recoveryCaseService;
    private final LossSentinelService lossSentinelService;
    private final AdminWorkflowService adminWorkflowService;
    private final ReturnPassService returnPassService;
    private final RecoveryCaseRepository recoveryCases;
    private final RecoveryMissionRepository recoveryMissions;
    private final PreventionAlertRepository preventionAlerts;
    private final ReturnPassRepository returnPasses;
    private final ClockService clock;
    private final InputSanitizer sanitizer;

    public DemoScenarioService(
            LostReportRepository lostReports,
            FoundItemRepository foundItems,
            ClaimRepository claims,
            AuditLogRepository auditLogs,
            RecoveryCaseService recoveryCaseService,
            LossSentinelService lossSentinelService,
            AdminWorkflowService adminWorkflowService,
            ReturnPassService returnPassService,
            RecoveryCaseRepository recoveryCases,
            RecoveryMissionRepository recoveryMissions,
            PreventionAlertRepository preventionAlerts,
            ReturnPassRepository returnPasses,
            ClockService clock,
            InputSanitizer sanitizer
    ) {
        this.lostReports = lostReports;
        this.foundItems = foundItems;
        this.claims = claims;
        this.auditLogs = auditLogs;
        this.recoveryCaseService = recoveryCaseService;
        this.lossSentinelService = lossSentinelService;
        this.adminWorkflowService = adminWorkflowService;
        this.returnPassService = returnPassService;
        this.recoveryCases = recoveryCases;
        this.recoveryMissions = recoveryMissions;
        this.preventionAlerts = preventionAlerts;
        this.returnPasses = returnPasses;
        this.clock = clock;
        this.sanitizer = sanitizer;
    }

    public DemoScenarioResponse create(String scenario, Map<String, Object> data, String adminEmail) {
        String normalized = scenario == null ? "" : scenario.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        return switch (normalized) {
            case "airpods_gym", "airpods_at_gym", "gym_airpods" -> airPodsAtGym(sanitized, adminEmail);
            case "approved_calculator_return", "calculator_return", "calculator" -> approvedCalculatorReturn(adminEmail);
            case "gym_electronics_pattern", "pattern_review", "loss_sentinel" -> gymElectronicsPattern(adminEmail);
            case "library_water_bottle", "water_bottle" -> libraryWaterBottle(sanitized, adminEmail);
            case "custom" -> customScenario(sanitized, adminEmail);
            default -> throw new BadRequestException("Unknown demo scenario. Use airpods_gym, approved_calculator_return, gym_electronics_pattern, library_water_bottle, or custom.");
        };
    }

    public Map<String, Object> cleanup(Map<String, Object> data, String adminEmail) {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(data);
        String confirmation = value(sanitized, "confirmation", value(sanitized, "confirm", ""));
        if (!"DELETE DEMO DATA".equals(confirmation)) {
            throw new BadRequestException("Cleanup requires confirmation: DELETE DEMO DATA");
        }

        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("return_passes", deleteDemoReturnPasses());
        deleted.put("claims", deleteDemoClaims());
        deleted.put("found_items", deleteDemoFoundItems());
        deleted.put("recovery_missions", deleteDemoMissions());
        deleted.put("recovery_cases", deleteDemoCases());
        deleted.put("prevention_alerts", deleteDemoAlerts());
        deleted.put("lost_reports", deleteDemoLostReports());

        audit("DEMO_SCENARIO_CLEANUP", "DemoScenario", "cleanup", adminEmail, "Deleted demo-only records after explicit confirmation.");
        return Map.of("success", true, "deleted", deleted, "confirmation", "DELETE DEMO DATA");
    }

    private DemoScenarioResponse airPodsAtGym(Map<String, Object> data, String adminEmail) {
        List<String> foundIds = new ArrayList<>();
        List<String> claimIds = new ArrayList<>();
        List<String> missionIds = new ArrayList<>();

        LostReport lost = lostReport(
                "lost_demo_airpods_" + shortId(),
                value(data, "title", "Lost AirPods at Gym"),
                "electronics",
                value(data, "description", "White AirPods case lost near the gym bleachers after practice."),
                "White",
                "Apple",
                "Gym Bleachers",
                "zone_gym_bleachers",
                value(data, "contact_email", "mia.rodriguez@pleasantvalley.edu"),
                "high",
                LocalDate.parse(clock.now().substring(0, 10)).toString()
        );
        lostReports.save(lost);
        RecoveryCase recoveryCase = recoveryCaseService.ensureForLostReport(lost);

        RecoveryMission bleachers = recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                "campus_zone_id", "zone_gym_bleachers",
                "zone_label", "Gym Bleachers",
                "title", "Sweep gym bleachers",
                "recommended_action", "Check under bleachers and compare any electronics against the Lost Report.",
                "priority", "high",
                "status", "open",
                "reasons", List.of("Last seen at gym", "High-value electronics")
        ), adminEmail);
        RecoveryMission entrance = recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                "campus_zone_id", "zone_gym_entrance",
                "zone_label", "Gym Entrance",
                "title", "Check gym entrance desk",
                "recommended_action", "Ask event staff whether AirPods were turned in after practice.",
                "priority", "medium",
                "status", "assigned",
                "assigned_to", adminEmail,
                "reasons", List.of("Common handoff location", "Near exit path")
        ), adminEmail);
        missionIds.add(bleachers.getId());
        missionIds.add(entrance.getId());

        boolean includeFoundItem = booleanValue(data, "include_found_item", true);
        boolean includeClaim = booleanValue(data, "include_claim", true);
        if (includeFoundItem) {
            FoundItem found = foundItem(
                    "found_demo_airpods_" + shortId(),
                    "White AirPods Case",
                    "electronics",
                    "White wireless earbud case found after gym practice.",
                    "White",
                    "Apple",
                    "Gym Bleachers",
                    "zone_gym_bleachers",
                    ItemStatus.FOUND
            );
            found.setPrivateVerificationClues(List.of("small blue sticker inside lid", "initials M.R. near hinge"));
            found.setStorageLocation("Main Office sealed bin A1");
            found.setLinkedLostReportId(lost.getId());
            foundItems.save(found);
            foundIds.add(found.getId());

            if (includeClaim) {
                Claim claim = claim(
                        "claim_demo_airpods_" + shortId(),
                        found.getId(),
                        "Mia Rodriguez",
                        lost.getContactEmail(),
                        "pending_review"
                );
                claim.setIdentifyingDetails("There is a small blue sticker inside the lid and my initials near the hinge.");
                claims.save(claim);
                claimIds.add(claim.getId());
                recoveryCaseService.update(recoveryCase.getId(), Map.of(
                        "selected_found_item_id", found.getId(),
                        "linked_claim_id", claim.getId(),
                        "status", "claim_in_review"
                ), adminEmail);
                found.setStatus(ItemStatus.CLAIM_PENDING);
                foundItems.save(found);
            } else {
                recoveryCaseService.update(recoveryCase.getId(), Map.of("selected_found_item_id", found.getId(), "status", "match_identified"), adminEmail);
            }
        }

        audit("DEMO_SCENARIO_CREATED", "DemoScenario", "airpods_gym", adminEmail, "Created AirPods at Gym demo scenario.");
        return new DemoScenarioResponse(
                "airpods_gym",
                List.of(lost.getId()),
                List.of(recoveryCase.getId()),
                missionIds,
                foundIds,
                claimIds,
                Map.of("next_step", "Open Recovery Center and review the pending AirPods claim.")
        );
    }

    private DemoScenarioResponse approvedCalculatorReturn(String adminEmail) {
        AppUser admin = adminUser(adminEmail);
        List<String> foundIds = new ArrayList<>();
        List<String> claimIds = new ArrayList<>();
        List<String> missionIds = new ArrayList<>();

        LostReport lost = lostReport(
                "lost_demo_calculator_" + shortId(),
                "Approved Calculator Return",
                "electronics",
                "Graphing calculator reported missing from math hallway before an approved pickup workflow.",
                "Black",
                "Texas Instruments",
                "Math Hall",
                "zone_main_office",
                "riley.chen@pleasantvalley.edu",
                "medium",
                LocalDate.parse(clock.now().substring(0, 10)).toString()
        );
        lostReports.save(lost);
        RecoveryCase recoveryCase = recoveryCaseService.ensureForLostReport(lost);

        FoundItem calculator = foundItem(
                "found_demo_calculator_" + shortId(),
                "TI-84 Graphing Calculator",
                "electronics",
                "Black graphing calculator turned in after math club.",
                "Black",
                "Texas Instruments",
                "Main Office",
                "zone_main_office",
                ItemStatus.FOUND
        );
        calculator.setEventHubId("hub_basketball_game");
        calculator.setPrivateVerificationClues(List.of("PVHS asset sticker on the back", "initials R.C. inside the case"));
        calculator.setStorageLocation("Main Office pickup drawer C2");
        calculator.setLinkedLostReportId(lost.getId());
        foundItems.save(calculator);
        foundIds.add(calculator.getId());

        Claim claim = claim(
                "claim_demo_calculator_" + shortId(),
                calculator.getId(),
                "Riley Chen",
                lost.getContactEmail(),
                "submitted"
        );
        claim.setIdentifyingDetails("The back has my PVHS asset sticker and initials R.C. inside the case.");
        claims.save(claim);

        Claim approved = adminWorkflowService.approveClaim(claim.getId(), admin, Map.of("admin_notes", "Approved demo calculator claim for pickup."));
        claimIds.add(approved.getId());

        RecoveryMission mission = recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                "campus_zone_id", "zone_main_office",
                "zone_label", "Main Office",
                "title", "Prepare approved calculator pickup",
                "recommended_action", "Hold the calculator at the pickup station and verify the Return Pass code.",
                "priority", "medium",
                "status", "assigned",
                "assigned_to", adminEmail,
                "is_demo", true
        ), adminEmail);
        missionIds.add(mission.getId());

        recoveryCaseService.update(recoveryCase.getId(), Map.of(
                "selected_found_item_id", calculator.getId(),
                "linked_claim_id", approved.getId(),
                "status", "claim_in_review",
                "event_hub_id", "hub_basketball_game",
                "campus_zone_id", "zone_main_office"
        ), adminEmail);

        ReturnPassResponse pass = returnPassService.create(
                approved.getId(),
                new ReturnPassRequest("Next school day during office hours", "PVHS Main Office pickup station"),
                admin
        );

        audit("DEMO_SCENARIO_CREATED", "DemoScenario", "approved_calculator_return", adminEmail, "Created Approved Calculator Return demo scenario.");
        return new DemoScenarioResponse(
                "approved_calculator_return",
                List.of(lost.getId()),
                List.of(recoveryCase.getId()),
                missionIds,
                foundIds,
                claimIds,
                Map.of(
                        "next_step", "Open Pickup Station to verify and redeem the generated Return Pass.",
                        "return_pass_id", pass.id(),
                        "pickup_location", pass.pickupLocation(),
                        "return_pass_status", pass.status()
                )
        );
    }

    private DemoScenarioResponse gymElectronicsPattern(String adminEmail) {
        LocalDate today = LocalDate.parse(clock.now().substring(0, 10));
        List<String> lostIds = new ArrayList<>();
        List<LocalDate> baselineDates = List.of(today.minusDays(31), today.minusDays(18));
        List<LocalDate> recentDates = List.of(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(4));

        int index = 1;
        for (LocalDate date : baselineDates) {
            LostReport report = lostReport(
                    "lost_demo_gym_base_" + shortId(),
                    "Baseline gym electronics report " + index,
                    "electronics",
                    "Historical electronics report used as Pattern Review baseline.",
                    "Black",
                    "Mixed",
                    "Gym Bleachers",
                    "zone_gym_bleachers",
                    "baseline" + index + "@pleasantvalley.edu",
                    "medium",
                    date.toString()
            );
            lostReports.save(report);
            lostIds.add(report.getId());
            index++;
        }

        index = 1;
        for (LocalDate date : recentDates) {
            LostReport report = lostReport(
                    "lost_demo_gym_recent_" + shortId(),
                    "Recent gym electronics report " + index,
                    "electronics",
                    "Recent electronics report from the gym pattern window.",
                    index % 2 == 0 ? "White" : "Black",
                    "Mixed",
                    "Gym Bleachers",
                    "zone_gym_bleachers",
                    "recent" + index + "@pleasantvalley.edu",
                    "medium",
                    date.toString()
            );
            lostReports.save(report);
            lostIds.add(report.getId());
            if (index == 1) {
                RecoveryCase recoveryCase = recoveryCaseService.ensureForLostReport(report);
                recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                        "campus_zone_id", "zone_gym_bleachers",
                        "zone_label", "Gym Bleachers",
                        "title", "Check electronics pattern source",
                        "recommended_action", "Review this source report while Pattern Review recomputes the gym electronics alert.",
                        "priority", "medium",
                        "status", "open"
                ), adminEmail);
            }
            index++;
        }

        PatternReviewResult result = lossSentinelService.recompute();
        audit("DEMO_SCENARIO_CREATED", "DemoScenario", "gym_electronics_pattern", adminEmail, "Created Gym Electronics Pattern demo scenario.");
        return new DemoScenarioResponse(
                "gym_electronics_pattern",
                lostIds,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("pattern_review_state", result.state(), "message", result.message(), "alerts", result.alerts())
        );
    }

    private DemoScenarioResponse libraryWaterBottle(Map<String, Object> data, String adminEmail) {
        LostReport lost = lostReport(
                "lost_demo_bottle_" + shortId(),
                value(data, "title", "Lost Library Water Bottle"),
                "food_containers",
                value(data, "description", "Clear water bottle left in the library study area."),
                "Clear",
                "Nalgene",
                "Library Study Area",
                "zone_library",
                value(data, "contact_email", "sam.taylor@pleasantvalley.edu"),
                "low",
                LocalDate.parse(clock.now().substring(0, 10)).toString()
        );
        lostReports.save(lost);
        RecoveryCase recoveryCase = recoveryCaseService.ensureForLostReport(lost);
        RecoveryMission mission = recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                "campus_zone_id", "zone_library",
                "zone_label", "Library Study Area",
                "title", "Check library lost shelf",
                "recommended_action", "Ask library staff to check the study tables and return shelf.",
                "priority", "low",
                "status", "open"
        ), adminEmail);
        audit("DEMO_SCENARIO_CREATED", "DemoScenario", "library_water_bottle", adminEmail, "Created Library Water Bottle demo scenario.");
        return new DemoScenarioResponse(
                "library_water_bottle",
                List.of(lost.getId()),
                List.of(recoveryCase.getId()),
                List.of(mission.getId()),
                List.of(),
                List.of(),
                Map.of("next_step", "Show this as a low-priority case in Recovery Center.")
        );
    }

    private DemoScenarioResponse customScenario(Map<String, Object> data, String adminEmail) {
        String category = value(data, "category", "personal_items");
        String location = value(data, "location_lost", value(data, "locationLost", "Main Office"));
        String zone = value(data, "campus_zone_id", value(data, "campusZoneId", "zone_main_office"));
        LostReport lost = lostReport(
                "lost_demo_custom_" + shortId(),
                value(data, "title", "Custom demo lost item"),
                category,
                value(data, "description", "Custom demo Lost Report created by admin scenario tooling."),
                value(data, "color", ""),
                value(data, "brand", ""),
                location,
                zone,
                value(data, "contact_email", "demo.student@pleasantvalley.edu"),
                value(data, "urgency", "medium"),
                value(data, "date_lost", LocalDate.parse(clock.now().substring(0, 10)).toString())
        );
        lostReports.save(lost);
        RecoveryCase recoveryCase = recoveryCaseService.ensureForLostReport(lost);
        RecoveryMission mission = recoveryCaseService.createMission(recoveryCase.getId(), Map.of(
                "campus_zone_id", zone,
                "zone_label", location,
                "title", "Review custom demo case",
                "recommended_action", "Follow up on the custom demo Lost Report and update status.",
                "priority", value(data, "urgency", "medium"),
                "status", "open"
        ), adminEmail);

        List<String> foundIds = new ArrayList<>();
        if (booleanValue(data, "include_found_item", false)) {
            FoundItem found = foundItem(
                    "found_demo_custom_" + shortId(),
                    value(data, "found_title", "Custom demo found item"),
                    category,
                    "Custom demo Found Item created separately from the Lost Report.",
                    lost.getColor(),
                    lost.getBrand(),
                    location,
                    zone,
                    ItemStatus.FOUND
            );
            found.setLinkedLostReportId(lost.getId());
            foundItems.save(found);
            foundIds.add(found.getId());
            recoveryCaseService.update(recoveryCase.getId(), Map.of("selected_found_item_id", found.getId(), "status", "match_identified"), adminEmail);
        }

        audit("DEMO_SCENARIO_CREATED", "DemoScenario", "custom", adminEmail, "Created custom demo scenario.");
        return new DemoScenarioResponse(
                "custom",
                List.of(lost.getId()),
                List.of(recoveryCase.getId()),
                List.of(mission.getId()),
                foundIds,
                List.of(),
                Map.of("next_step", "Use returned IDs to inspect the linked real domain records.")
        );
    }

    private LostReport lostReport(String id, String title, String category, String description, String color, String brand,
                                  String location, String zone, String email, String urgency, String date) {
        requireEmail(email);
        String now = clock.now();
        LostReport report = new LostReport();
        report.setId(id);
        report.setTitle(title);
        report.setCategory(category);
        report.setDescription(description);
        report.setColor(color);
        report.setBrand(brand);
        report.setLocationLost(location);
        report.setCampusZoneId(zone);
        report.setContactName("Demo Student");
        report.setContactEmail(email);
        report.setStatus("open");
        report.setUrgency(urgency);
        report.setDateLost(date);
        report.setCreatedDate(now);
        report.setUpdatedDate(now);
        report.setIsDemo(true);
        return report;
    }

    private FoundItem foundItem(String id, String title, String category, String description, String color, String brand,
                                String location, String zone, String status) {
        String now = clock.now();
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle(title);
        item.setCategory(category);
        item.setDescription(description);
        item.setColor(color);
        item.setBrand(brand);
        item.setLocationFound(location);
        item.setCampusZoneId(zone);
        item.setDateFound(now.substring(0, 10));
        item.setStatus(status);
        item.setRecordType("found");
        item.setRestrictedVisibility(false);
        item.setClaimConfirmed(false);
        item.setIsFlagged(false);
        item.setCreatedDate(now);
        item.setUpdatedDate(now);
        item.setIsDemo(true);
        return item;
    }

    private Claim claim(String id, String itemId, String name, String email, String status) {
        requireEmail(email);
        String now = clock.now();
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId(itemId);
        claim.setClaimantName(name);
        claim.setClaimantEmail(email);
        claim.setClaimReason("Demo scenario ownership claim for admin review.");
        claim.setStatus(status);
        claim.setRiskScore(10);
        claim.setRiskFlags(List.of("demo record"));
        claim.setCreatedDate(now);
        claim.setUpdatedDate(now);
        claim.setIsDemo(true);
        return claim;
    }

    private AppUser adminUser(String adminEmail) {
        AppUser admin = new AppUser();
        admin.setEmail(adminEmail);
        admin.setRole("admin");
        admin.setFullName("Demo Admin");
        return admin;
    }

    private int deleteDemoReturnPasses() {
        if (returnPasses == null) {
            return 0;
        }
        List<String> ids = returnPasses.findAll().stream()
                .filter(pass -> Boolean.TRUE.equals(pass.getIsDemo()))
                .map(pass -> pass.getId())
                .toList();
        ids.forEach(returnPasses::deleteById);
        return ids.size();
    }

    private int deleteDemoClaims() {
        List<String> ids = claims.findAll().stream()
                .filter(claim -> Boolean.TRUE.equals(claim.getIsDemo()))
                .map(Claim::getId)
                .toList();
        ids.forEach(claims::deleteById);
        return ids.size();
    }

    private int deleteDemoFoundItems() {
        List<String> ids = foundItems.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsDemo()))
                .map(FoundItem::getId)
                .toList();
        ids.forEach(foundItems::deleteById);
        return ids.size();
    }

    private int deleteDemoMissions() {
        if (recoveryMissions == null) {
            return 0;
        }
        List<String> ids = recoveryMissions.findAll().stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getIsDemo()))
                .map(RecoveryMission::getId)
                .toList();
        ids.forEach(recoveryMissions::deleteById);
        return ids.size();
    }

    private int deleteDemoCases() {
        if (recoveryCases == null) {
            return 0;
        }
        List<String> ids = recoveryCases.findAll().stream()
                .filter(recoveryCase -> Boolean.TRUE.equals(recoveryCase.getIsDemo()))
                .map(RecoveryCase::getId)
                .toList();
        ids.forEach(recoveryCases::deleteById);
        return ids.size();
    }

    private int deleteDemoAlerts() {
        if (preventionAlerts == null) {
            return 0;
        }
        List<String> ids = preventionAlerts.findAll().stream()
                .filter(alert -> Boolean.TRUE.equals(alert.getIsDemo()))
                .map(alert -> alert.getId())
                .toList();
        ids.forEach(preventionAlerts::deleteById);
        return ids.size();
    }

    private int deleteDemoLostReports() {
        List<String> ids = lostReports.findAll().stream()
                .filter(report -> Boolean.TRUE.equals(report.getIsDemo()))
                .map(LostReport::getId)
                .toList();
        ids.forEach(lostReports::deleteById);
        return ids.size();
    }

    private String value(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private boolean booleanValue(Map<String, Object> data, String key, boolean fallback) {
        Object value = data.get(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void requireEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("Scenario contact email must be valid.");
        }
    }

    private void audit(String action, String entityType, String entityId, String performedBy, String details) {
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

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
