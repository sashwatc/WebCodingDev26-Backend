package com.FBLA.WebCodingDev26Backend.config;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.model.PartnerRelay;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.PartnerRelayRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryMissionRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.service.CustodyLedgerService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

@Configuration
public class SeedDataConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedDataConfig.class);
    private static final String NOW = "2026-03-10T10:00:00Z";

    @Bean
    CommandLineRunner seedData(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            CampusZoneRepository campusZones,
            EventRecoveryHubRepository eventHubs,
            AssetRegistryRecordRepository assetRecords,
            RecoveryCaseRepository recoveryCases,
            RecoveryMissionRepository recoveryMissions,
            ReturnPassRepository returnPasses,
            PreventionAlertRepository preventionAlerts,
            NotificationDeliveryRepository notificationDeliveries,
            RecoveryNodeRepository recoveryNodes,
            PartnerRelayRepository partnerRelays,
            CustodyLedgerService custodyLedgerService,
            @Value("${app.seed.enabled}") boolean seedEnabled
    ) {
        return seedDataRunner(
                foundItems,
                lostReports,
                claims,
                notifications,
                auditLogs,
                users,
                campusZones,
                eventHubs,
                assetRecords,
                recoveryCases,
                recoveryMissions,
                returnPasses,
                preventionAlerts,
                notificationDeliveries,
                recoveryNodes,
                partnerRelays,
                custodyLedgerService,
                seedEnabled
        );
    }

    CommandLineRunner seedData(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            boolean seedEnabled
    ) {
        return seedDataRunner(foundItems, lostReports, claims, notifications, auditLogs, users, null, null, null, null, null, null, null, null, null, null, null, seedEnabled);
    }

    private CommandLineRunner seedDataRunner(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            CampusZoneRepository campusZones,
            EventRecoveryHubRepository eventHubs,
            AssetRegistryRecordRepository assetRecords,
            RecoveryCaseRepository recoveryCases,
            RecoveryMissionRepository recoveryMissions,
            ReturnPassRepository returnPasses,
            PreventionAlertRepository preventionAlerts,
            NotificationDeliveryRepository notificationDeliveries,
            RecoveryNodeRepository recoveryNodes,
            PartnerRelayRepository partnerRelays,
            CustodyLedgerService custodyLedgerService,
            boolean seedEnabled
    ) {
        return args -> {
            if (!seedEnabled) {
                return;
            }

            try {
                if (foundItems.count() > 0) {
                    return;
                }

                seedZones(campusZones);
                seedAssets(assetRecords);
                seedEvent(eventHubs);
                seedItems(foundItems);
                seedLostReports(lostReports);
                seedClaims(claims);
                seedRecovery(recoveryCases, recoveryMissions);
                seedPasses(returnPasses);
                seedRelay(recoveryNodes, partnerRelays);
                seedCustody(custodyLedgerService);

                notifications.save(notification("notif_001", "jordan.kim@pleasantvalley.edu", "Strong match available", "A strong possible match is ready for review.", "strong_item_match", "/UserDashboard", "found_002"));
                notifications.save(notification("notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "Return Pass ready", "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions.", "return_pass_ready", "/return-pass/pass_calculator_active", "found_claimed_calculator"));
                notifications.save(notification("notif_pattern_review_demo", "avery.patel@pleasantvalley.edu", "Pattern Review alert", "A loss pattern needs admin review.", "pattern_review_alert", "/admin/pattern-review", "alert_demo_pattern"));
                seedNotificationDeliveries(notificationDeliveries);
                auditLogs.save(auditLog());
                users.save(user("user_001", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "student"));
                users.save(user("user_002", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));
                users.save(user("user_003", "Riley Chen", "riley.chen@pleasantvalley.edu", "student"));
                users.save(user("user_staff_demo", "Demo Staff", "staff.demo@pleasantvalley.edu", "staff"));
                users.save(user("user_student_demo", "Demo Student", "student.demo@pleasantvalley.edu", "student"));
            } catch (DataAccessException exception) {
                LOGGER.warn("Skipping seed data because MongoDB is unavailable: {}", exception.getMessage());
            }
        };
    }

    private void seedZones(CampusZoneRepository campusZones) {
        if (campusZones == null) {
            return;
        }
        campusZones.saveAll(List.of(
                zone("zone_library", "Library Study Area"),
                zone("zone_gym_entrance", "Gym Entrance"),
                zone("zone_gym_bleachers", "Gym Bleachers"),
                zone("zone_cafeteria", "Cafeteria"),
                zone("zone_main_office", "Main Office"),
                zone("zone_bus_loop", "Bus Loop"),
                zone("zone_auditorium", "Auditorium"),
                zone("zone_athletics", "Athletics Office")
        ));
    }

    private void seedAssets(AssetRegistryRecordRepository assetRecords) {
        if (assetRecords == null) {
            return;
        }
        assetRecords.saveAll(List.of(
                asset("asset_cb_1042", "PVHS-CB-1042", "Chromebook", "Technology Office"),
                asset("asset_book_8821", "LIB-BOOK-8821", "Library Book", "Library Return Desk"),
                asset("asset_cam_027", "ATH-CAM-027", "Camera", "Athletics Office"),
                asset("asset_band_008", "BAND-INST-008", "Instrument", "Fine Arts Office")
        ));
    }

    private void seedEvent(EventRecoveryHubRepository eventHubs) {
        if (eventHubs == null) {
            return;
        }
        EventRecoveryHub hub = new EventRecoveryHub();
        hub.setId("hub_basketball_game");
        hub.setTenantId("pvhs");
        hub.setName("PVHS vs. Bettendorf Basketball Game");
        hub.setDescription("Demo integration-ready recovery hub for a high-traffic campus event.");
        hub.setEventType("athletics");
        hub.setStartTime("2026-03-14T18:00:00Z");
        hub.setEndTime("2026-03-14T21:00:00Z");
        hub.setStatus("active");
        hub.setCampusZoneIds(List.of("zone_gym_entrance", "zone_gym_bleachers", "zone_athletics"));
        hub.setPublicEnabled(true);
        hub.setDisplayEnabled(true);
        hub.setCreatedBy("avery.patel@pleasantvalley.edu");
        hub.setCreatedDate(NOW);
        hub.setUpdatedDate(NOW);
        eventHubs.save(hub);
    }

    private void seedItems(FoundItemRepository foundItems) {
        FoundItem bottle = foundItem("found_001", "Black Hydro Flask Water Bottle", "food_containers", "Matte black Hydro Flask bottle with a top carry handle and screw cap.", "Black", "Hydro Flask", "Gymnasium", "2026-03-11", "12:15", ItemStatus.FOUND, "FB-2026-HF82");
        bottle.setPhotoUrls(List.of("/items/black-hydro-flask.jpg"));
        bottle.setTags(List.of("water bottle", "hydro flask", "black", "gym"));
        bottle.setStorageLocation("Main Office shelf B2");
        bottle.setFinderName("Coach Miller");
        bottle.setFinderEmail("coach.miller@pleasantvalley.edu");
        bottle.setFinderRole("staff");

        FoundItem backpack = foundItem("found_002", "Blue JanSport Backpack", "bags_cases", "Royal blue JanSport backpack with math notebook and tennis keychain.", "Blue", "JanSport", "Student Lounge", "2026-03-09", "15:05", ItemStatus.VERIFIED, "FB-2026-JS27");
        backpack.setPhotoUrls(List.of("/images/blue-backpack.png"));
        backpack.setTags(List.of("backpack", "jansport", "blue", "student lounge"));
        backpack.setStorageLocation("Counselor office storage closet");

        FoundItem airpods = foundItem("found_airpods_game", "Black AirPods-style Case", "electronics", "Black wireless earbud case found after the basketball game.", "Black", "Apple", "Gym Bleachers", "2026-03-14", "20:40", ItemStatus.FOUND, "FB-2026-AP14");
        airpods.setEventHubId("hub_basketball_game");
        airpods.setCampusZoneId("zone_gym_bleachers");
        airpods.setPhotoUrls(List.of("/items/black-airpods-case.jpg"));
        airpods.setTags(List.of("airpods", "black", "earbuds", "gym bleachers"));
        airpods.setPrivateVerificationClues(List.of("small silver initials on the hinge", "tiny scratch along the left back corner"));
        airpods.setStorageLocation("Main Office sealed bin A1");

        FoundItem calculator = foundItem("found_calculator_demo", "Silver Graphing Calculator", "electronics", "Silver graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.FOUND, "FB-2026-CAL55");
        calculator.setEventHubId("hub_basketball_game");
        calculator.setCampusZoneId("zone_gym_entrance");
        calculator.setPrivateVerificationClues(List.of("name label under the slide cover", "faint star sticker on the back"));
        calculator.setTags(List.of("calculator", "silver", "texas instruments", "gym entrance"));
        calculator.setStorageLocation("Main Office sealed bin A2");

        FoundItem passItem = foundItem("found_claimed_calculator", "Silver Graphing Calculator", "electronics", "TI-style graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.VERIFIED, "FB-2026-CAL77");
        passItem.setEventHubId("hub_basketball_game");
        passItem.setCampusZoneId("zone_gym_entrance");
        passItem.setPrivateVerificationClues(List.of("name label under the slide cover"));
        passItem.setStorageLocation("Main Office pickup drawer");

        FoundItem returned = foundItem("found_returned_lanyard", "PVHS Lanyard With Keys", "personal_items", "Blue PVHS lanyard with two keys.", "Blue", "PVHS", "Athletics Office", "2026-03-13", "17:20", ItemStatus.ARCHIVED, "FB-2026-KEY22");
        returned.setEventHubId("hub_basketball_game");
        returned.setCampusZoneId("zone_athletics");
        returned.setClaimConfirmed(true);
        returned.setClaimConfirmedAt("2026-03-14T16:30:00Z");

        FoundItem chromebook = foundItem("found_asset_chromebook", "PVHS Chromebook", "electronics", "School-owned Chromebook with asset tag.", "Gray", "Lenovo", "Library Study Area", "2026-03-12", "11:05", ItemStatus.FOUND, "FB-2026-CB1042");
        chromebook.setAssetTag("PVHS-CB-1042");
        chromebook.setAssetRecordId("asset_cb_1042");
        chromebook.setDepartmentDestination("Technology Office");
        chromebook.setRestrictedVisibility(true);
        chromebook.setStorageLocation("Technology Office intake shelf");

        foundItems.saveAll(List.of(bottle, backpack, airpods, calculator, passItem, returned, chromebook));
    }

    private void seedLostReports(LostReportRepository lostReports) {
        LostReport report = lostReport("lost_001", "Missing blue backpack", "bags_cases", "Blue JanSport backpack with a tennis keychain.", "Blue", "JanSport", "Student Lounge", "2026-03-09", "jordan.kim@pleasantvalley.edu");
        report.setMatchedItems(List.of(match("found_002", "Blue JanSport Backpack", 96)));
        lostReports.save(report);

        LostReport airpods = lostReport("lost_airpods_game", "Lost black AirPods-style case", "electronics", "Black earbud case lost during the PVHS vs. Bettendorf game.", "Black", "Apple", "Gym Bleachers", "2026-03-14", "mia.rodriguez@pleasantvalley.edu");
        airpods.setEventHubId("hub_basketball_game");
        airpods.setCampusZoneId("zone_gym_bleachers");
        airpods.setUrgency("high");
        airpods.setMatchedItems(List.of(match("found_airpods_game", "Black AirPods-style Case", 94)));
        lostReports.save(airpods);

        LostReport calculator = lostReport("lost_calculator_demo", "Lost silver graphing calculator", "electronics", "Silver Texas Instruments calculator with a name label under the slide cover.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "riley.chen@pleasantvalley.edu");
        calculator.setEventHubId("hub_basketball_game");
        calculator.setCampusZoneId("zone_gym_entrance");
        calculator.setUrgency("high");
        calculator.setMatchedItems(List.of(match("found_calculator_demo", "Silver Graphing Calculator", 97)));
        lostReports.save(calculator);
    }

    private void seedClaims(ClaimRepository claims) {
        Claim claim = claim("claim_001", "found_002", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "approved");
        claims.save(claim);

        Claim review = claim("claim_airpods_review", "found_airpods_game", "Mia Rodriguez", "mia.rodriguez@pleasantvalley.edu", "pending_review");
        review.setEvidenceChecklist(List.of("hidden mark", "case condition", "last known location"));
        review.setPrivateEvidenceResponses(java.util.Map.of("hidden_mark", "silver initials on the hinge", "condition", "scratch on the back corner"));
        review.setIdentifyingDetails("It has my small silver initials and a scratch on the back left corner.");
        review.setVerificationScore(88);
        review.setVerificationFlags(List.of("strong overlap", "proof photo supplied"));
        review.setVerificationSummary("Claim evidence strongly overlaps with sealed verification clues. Staff review is still required.");
        claims.save(review);

        Claim approved = claim("claim_calculator_approved", "found_claimed_calculator", "Riley Chen", "riley.chen@pleasantvalley.edu", "approved");
        approved.setIdentifyingDetails("Name label under the slide cover.");
        claims.save(approved);

        Claim completed = claim("claim_keys_completed", "found_returned_lanyard", "Avery Brooks", "avery.brooks@pleasantvalley.edu", "completed");
        completed.setReceivedConfirmedAt("2026-03-14T16:30:00Z");
        claims.save(completed);
    }

    private void seedRecovery(RecoveryCaseRepository recoveryCases, RecoveryMissionRepository recoveryMissions) {
        if (recoveryCases == null || recoveryMissions == null) {
            return;
        }
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId("case_airpods_game");
        recoveryCase.setCaseCode("PVHS-RM-20260314-AIRP");
        recoveryCase.setTenantId("pvhs");
        recoveryCase.setLostReportId("lost_airpods_game");
        recoveryCase.setSelectedFoundItemId("found_airpods_game");
        recoveryCase.setLinkedClaimId("claim_airpods_review");
        recoveryCase.setEventHubId("hub_basketball_game");
        recoveryCase.setCampusZoneId("zone_gym_bleachers");
        recoveryCase.setStatus("claim_in_review");
        recoveryCase.setPriority("high");
        recoveryCase.setAssignedTo("avery.patel@pleasantvalley.edu");
        recoveryCase.setSummary("Lost black AirPods-style case from the basketball game.");
        recoveryCase.setRecoveryPlan("Likely Recovery Zones\n1. Gym Bleachers - 86%\n2. Gym Entrance - 61%\n3. Athletics Office - 35%\n\nWhy:\n- Last seen near gym\n- Similar electronics were found nearby\n- Event workflow is active");
        recoveryCase.setLikelyZoneSummaries(List.of("Gym Bleachers - 86%", "Gym Entrance - 61%", "Athletics Office - 35%"));
        recoveryCase.setCreatedDate(NOW);
        recoveryCase.setUpdatedDate(NOW);
        recoveryCase.setIsDemo(true);
        recoveryCases.save(recoveryCase);

        recoveryMissions.save(mission("mission_bleachers", "case_airpods_game", "zone_gym_bleachers", "Gym Bleachers", 86, "high", "open"));
        recoveryMissions.save(mission("mission_entrance", "case_airpods_game", "zone_gym_entrance", "Gym Entrance", 61, "medium", "checked"));
    }

    private void seedPasses(ReturnPassRepository returnPasses) {
        if (returnPasses == null) {
            return;
        }
        ReturnPass active = pass("pass_calculator_active", "claim_calculator_approved", "found_claimed_calculator", "riley.chen@pleasantvalley.edu", "active", "314159");
        returnPasses.save(active);
        ReturnPass redeemed = pass("pass_keys_redeemed", "claim_keys_completed", "found_returned_lanyard", "avery.brooks@pleasantvalley.edu", "redeemed", "271828");
        redeemed.setRedeemedAt("2026-03-14T16:30:00Z");
        redeemed.setRedeemedBy("avery.patel@pleasantvalley.edu");
        returnPasses.save(redeemed);
    }

    private void seedRelay(RecoveryNodeRepository recoveryNodes, PartnerRelayRepository partnerRelays) {
        if (recoveryNodes == null || partnerRelays == null) {
            return;
        }
        recoveryNodes.saveAll(List.of(
                node("node_main_office", "PVHS Main Office"),
                node("node_athletics", "PVHS Athletics"),
                node("node_transportation", "PVHS Transportation"),
                node("node_fine_arts", "PVHS Fine Arts")
        ));
        PartnerRelay relay = new PartnerRelay();
        relay.setId("relay_airpods_athletics");
        relay.setSourceNodeId("node_main_office");
        relay.setTargetNodeId("node_athletics");
        relay.setRecoveryCaseId("case_airpods_game");
        relay.setFoundItemId("found_airpods_game");
        relay.setStatus("awaiting_verification");
        relay.setPublicSummary("A possible match may be available at a partner location. Submit ownership evidence to continue.");
        relay.setRedactedMatchReasons(List.of("Category and color overlap", "Event zone context overlaps"));
        relay.setCreatedDate(NOW);
        relay.setUpdatedDate(NOW);
        partnerRelays.save(relay);
    }

    private void seedCustody(CustodyLedgerService custodyLedgerService) {
        if (custodyLedgerService == null) {
            return;
        }
        custodyLedgerService.appendEvent("found_airpods_game", "intake_created", "coach.miller@pleasantvalley.edu", "staff", "Main Office sealed bin A1", "Event item intake created.", null);
        custodyLedgerService.appendEvent("found_airpods_game", "reviewed", "avery.patel@pleasantvalley.edu", "admin", "Main Office sealed bin A1", "Proof Vault clues sealed.", null);
        custodyLedgerService.appendEvent("found_airpods_game", "matched", "system@pvhs.demo", "system", "", "Advisory match linked to lost report.", null);
        custodyLedgerService.appendEvent("found_claimed_calculator", "pickup_ready", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Active Return Pass available.", null);
        custodyLedgerService.appendEvent("found_returned_lanyard", "handoff_verified", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Manual code verified.", null);
        custodyLedgerService.appendEvent("found_returned_lanyard", "returned", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Item returned to verified claimant.", null);
    }

    private FoundItem foundItem(String id, String title, String category, String description, String color, String brand,
                               String location, String date, String time, String status, String itemCode) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle(title);
        item.setCategory(category);
        item.setDescription(description);
        item.setColor(color);
        item.setBrand(brand);
        item.setLocationFound(location);
        item.setDateFound(date);
        item.setTimeFound(time);
        item.setStatus(status);
        item.setRecordType("found");
        item.setItemCode(itemCode);
        item.setIsFlagged(false);
        item.setClaimConfirmed(false);
        item.setRestrictedVisibility(false);
        item.setCreatedDate(NOW);
        item.setUpdatedDate(NOW);
        item.setIsDemo(true);
        return item;
    }

    private LostReport lostReport(String id, String title, String category, String description, String color, String brand, String location, String date, String email) {
        LostReport report = new LostReport();
        report.setId(id);
        report.setTitle(title);
        report.setCategory(category);
        report.setDescription(description);
        report.setColor(color);
        report.setBrand(brand);
        report.setLocationLost(location);
        report.setDateLost(date);
        report.setContactName("Demo Student");
        report.setContactEmail(email);
        report.setStatus("open");
        report.setUrgency("medium");
        report.setCreatedDate(NOW);
        report.setUpdatedDate(NOW);
        report.setIsDemo(true);
        return report;
    }

    private MatchSuggestion match(String itemId, String title, int confidence) {
        MatchSuggestion suggestion = new MatchSuggestion();
        suggestion.setFoundItemId(itemId);
        suggestion.setFoundItemTitle(title);
        suggestion.setConfidence(confidence);
        suggestion.setReasons(List.of("category match", "color match", "location is similar"));
        suggestion.setSource("seed");
        suggestion.setStatus("suggested");
        suggestion.setCreatedDate(NOW);
        suggestion.setUpdatedDate(NOW);
        return suggestion;
    }

    private Claim claim(String id, String itemId, String name, String email, String status) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId(itemId);
        claim.setClaimantName(name);
        claim.setClaimantEmail(email);
        claim.setClaimReason("Seeded demo claim for PVHS Recovery Mesh.");
        claim.setIdentifyingDetails("Seeded identifying details.");
        claim.setStatus(status);
        claim.setRiskScore(10);
        claim.setRiskFlags(List.of("demo record"));
        claim.setCreatedDate(NOW);
        claim.setUpdatedDate(NOW);
        claim.setIsDemo(true);
        return claim;
    }

    private ReturnPass pass(String id, String claimId, String itemId, String email, String status, String code) {
        ReturnPass pass = new ReturnPass();
        pass.setId(id);
        pass.setClaimId(claimId);
        pass.setFoundItemId(itemId);
        pass.setClaimantEmail(email);
        pass.setPickupWindow("Next school day during office hours");
        pass.setPickupLocation("PVHS Main Office pickup station");
        pass.setStatus(status);
        pass.setOneTimeCode(code);
        pass.setToken("seeded-demo-token-not-public");
        pass.setExpiresAt("2026-12-31T23:59:00Z");
        pass.setCreatedDate(NOW);
        pass.setUpdatedDate(NOW);
        return pass;
    }

    private RecoveryMission mission(String id, String caseId, String zoneId, String zoneLabel, int score, String priority, String status) {
        RecoveryMission mission = new RecoveryMission();
        mission.setId(id);
        mission.setRecoveryCaseId(caseId);
        mission.setEventHubId("hub_basketball_game");
        mission.setCampusZoneId(zoneId);
        mission.setZoneLabel(zoneLabel);
        mission.setTitle("Check " + zoneLabel);
        mission.setRecommendedAction("Staff should check this zone and compare any found item with the lost report.");
        mission.setReasons(List.of("Last seen near gym", "Event workflow is active"));
        mission.setScore(score);
        mission.setPriority(priority);
        mission.setStatus(status);
        mission.setCreatedDate(NOW);
        mission.setUpdatedDate(NOW);
        mission.setIsDemo(true);
        return mission;
    }

    private CampusZone zone(String id, String label) {
        CampusZone zone = new CampusZone();
        zone.setId(id);
        zone.setLabel(label);
        zone.setDescription("Seeded PVHS campus recovery zone.");
        zone.setCreatedDate(NOW);
        zone.setUpdatedDate(NOW);
        return zone;
    }

    private AssetRegistryRecord asset(String id, String tag, String type, String destination) {
        AssetRegistryRecord asset = new AssetRegistryRecord();
        asset.setId(id);
        asset.setAssetTag(tag);
        asset.setAssetType(type);
        asset.setDepartmentDestination(destination);
        asset.setStatus("active");
        asset.setCreatedDate(NOW);
        asset.setUpdatedDate(NOW);
        return asset;
    }

    private RecoveryNode node(String id, String name) {
        RecoveryNode node = new RecoveryNode();
        node.setId(id);
        node.setName(name);
        node.setNodeType("demo_partner");
        node.setStatus("active");
        node.setCreatedDate(NOW);
        node.setUpdatedDate(NOW);
        return node;
    }

    private Notification notification(String id, String email, String title, String message, String type, String link, String itemId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRelatedItemId(itemId);
        notification.setIsRead(false);
        notification.setCreatedDate(NOW);
        notification.setUpdatedDate(NOW);
        return notification;
    }

    private void seedNotificationDeliveries(NotificationDeliveryRepository notificationDeliveries) {
        if (notificationDeliveries == null) {
            return;
        }
        notificationDeliveries.saveAll(List.of(
                delivery("ndel_return_pass_email", "notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "email", "return_pass_ready", "mock_sent", "mock_email", "Return Pass ready - Your Return Pass is ready."),
                delivery("ndel_return_pass_sms", "notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "sms", "return_pass_ready", "mock_sent", "mock_sms", "Return Pass ready - Your Return Pass is ready."),
                delivery("ndel_strong_match_email", "notif_001", "jordan.kim@pleasantvalley.edu", "email", "strong_item_match", "mock_sent", "mock_email", "Strong match available - A strong possible match is ready."),
                delivery("ndel_pattern_webhook", "notif_pattern_review_demo", "avery.patel@pleasantvalley.edu", "webhook", "pattern_review_alert", "mock_sent", "mock_webhook", "Pattern Review alert - A loss pattern needs admin review.")
        ));
    }

    private NotificationDelivery delivery(String id, String notificationId, String email, String channel, String eventType, String status, String provider, String preview) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(id);
        delivery.setNotificationId(notificationId);
        delivery.setRecipientUserEmail(email);
        if ("email".equals(channel)) {
            delivery.setRecipientEmail(email);
        }
        if ("sms".equals(channel)) {
            delivery.setRecipientPhoneMasked("demo-masked");
        }
        delivery.setChannel(channel);
        delivery.setEventType(eventType);
        delivery.setDeliveryStatus(status);
        delivery.setProvider(provider);
        delivery.setProviderMessageId(provider + "_seed_demo");
        delivery.setCreatedDate(NOW);
        delivery.setSentDate(NOW);
        delivery.setSafeMessagePreview(preview);
        delivery.setIsDemo(true);
        return delivery;
    }

    private AuditLog auditLog() {
        AuditLog auditLog = new AuditLog();
        auditLog.setId("audit_001");
        auditLog.setAction("Seed data created");
        auditLog.setEntityType("system");
        auditLog.setEntityId("seed");
        auditLog.setPerformedBy("system");
        auditLog.setDetails("PVHS Recovery Mesh NLC demo records loaded.");
        auditLog.setCreatedDate(NOW);
        return auditLog;
    }

    private AppUser user(String id, String fullName, String email, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setAvatarUrl("");
        user.setEmailNotificationsEnabled(true);
        user.setSmsOptIn(false);
        user.setSmsNotificationsEnabled(false);
        user.setWebhookNotificationsEnabled(true);
        user.setNotificationCategories(List.of("all"));
        user.setCreatedDate(NOW);
        user.setUpdatedDate(NOW);
        return user;
    }
}
