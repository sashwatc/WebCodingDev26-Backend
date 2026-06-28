package com.FBLA.WebCodingDev26Backend.config;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryNode;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.PreventionAlertRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryNodeRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.service.CustodyLedgerService;
import com.FBLA.WebCodingDev26Backend.service.SystemSettingService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

/**
 * Central demo/seed-data configuration for the PVHS Recovery Mesh backend.
 *
 * <p>This {@link Configuration} class registers {@link CommandLineRunner} beans
 * that populate MongoDB at startup with a coherent set of demo records — found
 * items, lost reports, claims, return passes, recovery cases, campus zones, asset
 * registry records, notifications, deliveries, audit logs, custody-ledger events,
 * and user accounts — so the app shows a realistic, scripted dataset out of the
 * box for live demos.</p>
 *
 * <p>It wires together nearly every repository and a couple of services
 * ({@code CustodyLedgerService}, {@code SystemSettingService}). All seeding is
 * gated on the {@code app.seed.enabled} property and written to be idempotent
 * (insert-if-absent / upsert helpers) so it self-heals onto an already-seeded
 * database without creating duplicates, and so it degrades gracefully (logging a
 * warning) when MongoDB is unavailable. The private builder helpers near the
 * bottom construct fully-populated model objects used by the seed routines.</p>
 */
@Configuration
public class SeedDataConfig {
    // Logger used to warn (not fail) when MongoDB is unreachable during seeding.
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedDataConfig.class);
    // Fixed ISO-8601 timestamp applied as created/updated date across seeded records for determinism.
    private static final String NOW = "2026-03-10T10:00:00Z";

    /**
     * Primary seed bean: the full demo dataset runner, wired with every
     * repository/service it needs. Spring injects all the beans and the
     * {@code app.seed.*} property values, and runs the returned
     * {@link CommandLineRunner} at startup.
     *
     * <p>This method only assembles dependencies and delegates to
     * {@link #seedDataRunner}, which contains the actual logic. Keeping the logic
     * in a separate package-private method lets tests build a runner directly.</p>
     *
     * @param foundItems            repository for found-item documents
     * @param lostReports           repository for lost-report documents
     * @param claims                repository for claim documents
     * @param notifications         repository for user notifications
     * @param auditLogs             repository for audit-log entries
     * @param users                 repository for app user accounts
     * @param campusZones           repository for campus recovery zones
     * @param assetRecords          repository for school asset-registry records
     * @param recoveryCases         repository for recovery cases
     * @param returnPasses          repository for return passes (pickup credentials)
     * @param preventionAlerts      repository for prevention/pattern alerts (injected; unused here)
     * @param notificationDeliveries repository for notification delivery records
     * @param recoveryNodes         repository for recovery relay nodes
     * @param custodyLedgerService  service that appends chain-of-custody events
     * @param systemSettings        service for default system settings (categories, pickup info)
     * @param seedEnabled           {@code app.seed.enabled}; master switch for all seeding
     * @param pickupLocation        configured default pickup location (with fallback default)
     * @param pickupHours           configured default pickup hours (with fallback default)
     * @return a runner that seeds the full demo dataset when enabled
     */
    @Bean
    CommandLineRunner seedData(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            CampusZoneRepository campusZones,
            AssetRegistryRecordRepository assetRecords,
            RecoveryCaseRepository recoveryCases,
            ReturnPassRepository returnPasses,
            PreventionAlertRepository preventionAlerts,
            NotificationDeliveryRepository notificationDeliveries,
            RecoveryNodeRepository recoveryNodes,
            CustodyLedgerService custodyLedgerService,
            SystemSettingService systemSettings,
            @Value("${app.seed.enabled}") boolean seedEnabled,
            @Value("${app.pickup.location:PVHS Main Office pickup station}") String pickupLocation,
            @Value("${app.pickup.hours:School days, 8:00 AM-3:30 PM}") String pickupHours
    ) {
        // Delegate to the shared runner builder with all injected collaborators.
        return seedDataRunner(
                foundItems,
                lostReports,
                claims,
                notifications,
                auditLogs,
                users,
                campusZones,
                assetRecords,
                recoveryCases,
                returnPasses,
                preventionAlerts,
                notificationDeliveries,
                recoveryNodes,
                custodyLedgerService,
                systemSettings,
                pickupLocation,
                pickupHours,
                seedEnabled
        );
    }

    /**
     * Test-friendly overload that builds a seed runner with only the core
     * repositories, passing {@code null} for the optional collaborators. The
     * seeding helpers all null-check their optional dependencies, so the runner
     * safely skips the corresponding sections.
     *
     * @param foundItems    repository for found-item documents
     * @param lostReports   repository for lost-report documents
     * @param claims        repository for claim documents
     * @param notifications repository for user notifications
     * @param auditLogs     repository for audit-log entries
     * @param users         repository for app user accounts
     * @param seedEnabled   master switch for all seeding
     * @return a runner that seeds only the core demo records when enabled
     */
    CommandLineRunner seedData(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            boolean seedEnabled
    ) {
        // Optional collaborators are null; the runner's null-guards skip their sections.
        return seedDataRunner(foundItems, lostReports, claims, notifications, auditLogs, users, null, null, null, null, null, null, null, null, null, null, null, seedEnabled);
    }

    /**
     * Seeds the scripted live-demo narrative (Case 042 — Avery Chen's navy Owala
     * bottle, end-to-end; and Case 041 — Jordan Lee's AirPods, the pre-seeded
     * Case Chat / needs-info example). Runs as its own idempotent CommandLineRunner
     * so every record is inserted only when absent — this self-heals onto an
     * already-seeded database without touching the main seed or its tests.
     */
    @Bean
    CommandLineRunner seedDemoNarrative(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            ReturnPassRepository returnPasses,
            CaseMessageRepository caseMessages,
            AppUserRepository users,
            @Value("${app.seed.enabled}") boolean seedEnabled
    ) {
        return args -> {
            // Respect the global seed switch.
            if (!seedEnabled) {
                return;
            }
            try {
                // Insert the scripted Case 041/042 narrative records (idempotently).
                seedNarrativeShowcase(foundItems, lostReports, claims, notifications, returnPasses, caseMessages, users);
            } catch (DataAccessException exception) {
                // Degrade gracefully: log and continue if the database is down.
                LOGGER.warn("Skipping demo narrative seed because MongoDB is unavailable: {}", exception.getMessage());
            }
        };
    }

    /**
     * Builds the two scripted live-demo cases end to end: Case 042 (Avery Chen's
     * navy Owala bottle, the full recovery journey through to a redeemable Return
     * Pass) and Case 041 (Jordan Lee's AirPods, sitting in "needs more info" with a
     * pre-seeded case chat). Every record is inserted only when absent, and the
     * active Return Pass + calculator backup are reset to a redeemable state on each
     * run so rehearsals are restored by a simple restart.
     *
     * @param foundItems   repository for the demo found items
     * @param lostReports  repository for the matching lost reports
     * @param claims       repository for the demo claims
     * @param notifications repository for the demo notifications
     * @param returnPasses repository for the demo return passes (may be null in tests)
     * @param caseMessages repository for the case-chat messages
     * @param users        repository for the demo student accounts (may be null in tests)
     */
    private void seedNarrativeShowcase(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            ReturnPassRepository returnPasses,
            CaseMessageRepository caseMessages,
            AppUserRepository users
    ) {
        // Demo students who own the two narrative cases (kept distinct from admin
        // Avery Patel). They can sign in via demo mode with name + email.
        if (users != null) {
            users.save(user("user_avery_chen", "Avery Chen", "avery.chen@pleasantvalley.edu", "student"));
            users.save(user("user_jordan_lee", "Jordan Lee", "jordan.lee@pleasantvalley.edu", "student"));
        }

        // ---- Case 042 — Avery Chen's navy Owala bottle (full recovery journey) ----
        // CLAIM_PENDING keeps the item viewable on the public detail page (VERIFIED
        // items are hidden by the privacy filter); the active Return Pass still
        // redeems it to ARCHIVED live during the demo.
        FoundItem owala = foundItem("found_owala", "Navy Owala FreeSip Water Bottle", "food_containers",
                "Navy Owala FreeSip water bottle with a yellow lightning-bolt sticker on the front.",
                "Navy", "Owala", "North Gym Lobby", "2026-03-09", "15:05", ItemStatus.CLAIM_PENDING, "FB-2026-OWL42");
        owala.setTags(List.of("water bottle", "owala", "navy", "north gym", "freesip"));
        owala.setPhotoUrls(List.of(
                "/items/navy-owala-bottle.svg",
                "/items/navy-owala-bottle-2.svg",
                "/items/navy-owala-bottle-3.svg"));
        owala.setPrivateVerificationClues(List.of(
                "yellow lightning-bolt sticker on the front",
                "small dent near the base",
                "initials 'A.C.' written under the lid"));
        owala.setStorageLocation("Front Office");
        owala.setFinderName("Coach Miller");
        owala.setFinderEmail("coach.miller@pleasantvalley.edu");
        owala.setFinderRole("staff");
        owala.setClaimConfirmed(true);
        saveSeeded(foundItems, owala);

        LostReport owalaLost = lostReport("lost_owala", "Lost navy Owala water bottle", "food_containers",
                "Navy Owala FreeSip bottle with a yellow lightning sticker, last seen in the North Gym.",
                "Navy", "Owala", "North Gym", "2026-03-09", "avery.chen@pleasantvalley.edu");
        owalaLost.setContactName("Avery Chen");
        owalaLost.setUrgency("high");
        owalaLost.setMatchedItems(List.of(match("found_owala", "Navy Owala FreeSip Water Bottle", 96)));
        saveSeeded(lostReports, owalaLost);

        // under_review keeps Avery's claim in the staff work queue and evidence
        // review (so the demo can show it), while the pre-issued active Return Pass
        // still redeems it live without depending on a live verify step.
        Claim owalaClaim = claim("claim_owala", "found_owala", "Avery Chen", "avery.chen@pleasantvalley.edu", "under_review");
        owalaClaim.setFoundItemTitle("Navy Owala FreeSip Water Bottle");
        owalaClaim.setClaimReason("This is my navy Owala — I left it in the North Gym after practice.");
        owalaClaim.setIdentifyingDetails("It has a yellow lightning-bolt sticker on the front and my initials under the lid.");
        owalaClaim.setEvidenceChecklist(List.of("sticker on the front", "initials under the lid", "last seen North Gym"));
        owalaClaim.setVerificationScore(96);
        owalaClaim.setVerificationFlags(List.of("matches sealed clue", "specific hidden detail"));
        owalaClaim.setVerificationSummary("Claim details match the sealed verification clues; pending final staff verification.");
        saveSeeded(claims, owalaClaim);

        // Active Return Pass, ready to redeem live at the Front Office pickup desk.
        // Overwritten on every startup so a rehearsal that redeems the pass is fully
        // restored by a simple restart (status reset to active, redeemed fields cleared).
        if (returnPasses != null) {
            ReturnPass owalaPass = pass("pass_owala_active", "claim_owala", "found_owala", "avery.chen@pleasantvalley.edu", "active", "161803");
            owalaPass.setPickupLocation("Front Office");
            returnPasses.save(owalaPass);

            // Restore the calculator backup pass + item to a redeemable state on every
            // startup (backup PIN 314159). Redemption now archives rather than deletes,
            // and these backup records are not otherwise re-seeded on a populated DB.
            returnPasses.findById("pass_calculator_active").ifPresent(p -> {
                p.setStatus("active");
                p.setRedeemedAt(null);
                p.setRedeemedBy(null);
                returnPasses.save(p);
            });
            if (foundItems != null) {
                foundItems.findById("found_claimed_calculator").ifPresent(it -> {
                    it.setStatus(ItemStatus.VERIFIED);
                    it.setClaimConfirmed(false);
                    foundItems.save(it);
                });
            }
        }

        // Case Chat history on Avery's claim: staff verification exchange.
        seedCaseMessage(caseMessages, "msg_owala_1", "claim_owala", "avery.patel@pleasantvalley.edu", "admin",
                "Thanks for your claim. Can you confirm a detail only the owner would know about the bottle?", "2026-03-10T09:05:00Z");
        seedCaseMessage(caseMessages, "msg_owala_2", "claim_owala", "avery.chen@pleasantvalley.edu", "student",
                "It has a yellow lightning-bolt sticker on the front and my initials under the lid.", "2026-03-10T09:12:00Z");
        seedCaseMessage(caseMessages, "msg_owala_3", "claim_owala", "avery.patel@pleasantvalley.edu", "admin",
                "Thanks — that matches the sealed verification note. We're finalizing your claim; your Return Pass will be ready for pickup at the Front Office.", "2026-03-10T09:30:00Z");

        saveIfMissing(notifications, notification("notif_owala_match", "avery.chen@pleasantvalley.edu",
                "Possible match available", "A navy Owala bottle may match your lost report.", "strong_item_match", "/UserDashboard", "found_owala"));
        saveIfMissing(notifications, notification("notif_owala_pass", "avery.chen@pleasantvalley.edu",
                "Return Pass ready", "Your Return Pass for the navy Owala bottle is ready for pickup at the Front Office.", "return_pass_ready", "/UserDashboard", "found_owala"));

        // ---- Case 041 — Jordan Lee's AirPods (pre-seeded Case Chat / needs-info) ----
        FoundItem airpods = foundItem("found_airpods_041", "White AirPods Pro Case", "electronics",
                "White AirPods Pro charging case found after the basketball game.",
                "White", "Apple", "Gym Bleachers", "2026-03-14", "20:45", ItemStatus.CLAIM_PENDING, "FB-2026-APD41");
        airpods.setTags(List.of("airpods", "apple", "white", "earbuds"));
        airpods.setPrivateVerificationClues(List.of("hairline scratch on the lid", "engraved initials 'J.L.' inside"));
        airpods.setStorageLocation("Main Office sealed bin A3");
        airpods.setFinderName("Coach Miller");
        airpods.setFinderEmail("coach.miller@pleasantvalley.edu");
        airpods.setFinderRole("staff");
        airpods.setPhotoUrls(List.of("/items/airpods-pro-case.png"));
        saveSeeded(foundItems, airpods);

        Claim airpodsClaim = claim("claim_airpods_041", "found_airpods_041", "Jordan Lee", "jordan.lee@pleasantvalley.edu", "need_more_info");
        airpodsClaim.setFoundItemTitle("White AirPods Pro Case");
        airpodsClaim.setClaimReason("I lost my AirPods Pro at the basketball game.");
        airpodsClaim.setIdentifyingDetails("White AirPods Pro case.");
        airpodsClaim.setAdminNotes("Ask the claimant to confirm a hidden marking before approving.");
        airpodsClaim.setEvidenceChecklist(List.of("hidden marking", "engraving", "last seen location"));
        airpodsClaim.setVerificationScore(58);
        airpodsClaim.setVerificationFlags(List.of("needs one more private detail"));
        airpodsClaim.setVerificationSummary("Partial match — one more private verification detail is needed.");
        saveSeeded(claims, airpodsClaim);

        // One staff message; the claim sits in need_more_info awaiting the student's reply.
        seedCaseMessage(caseMessages, "msg_ap041_1", "claim_airpods_041", "staff.demo@pleasantvalley.edu", "staff",
                "Thanks for the claim. Can you describe any hidden marking or engraving on the case?", "2026-03-14T21:00:00Z");

        saveIfMissing(notifications, notification("notif_ap041_info", "jordan.lee@pleasantvalley.edu",
                "More info needed", "Staff requested one more detail for your AirPods claim.", "claim_more_info", "/UserDashboard", "found_airpods_041"));
    }

    /**
     * Inserts a single case-chat message tied to a claim, only if one with the
     * given id does not already exist (idempotent).
     *
     * @param repository case-message repository (no-op when null)
     * @param id         stable message id used for the existence check
     * @param claimId    id of the claim this message belongs to
     * @param senderId   email/identifier of the sender
     * @param senderRole role of the sender (e.g. admin, staff, student)
     * @param message    the message body text
     * @param createdAt  ISO-8601 timestamp for the message
     */
    private void seedCaseMessage(CaseMessageRepository repository, String id, String claimId, String senderId, String senderRole, String message, String createdAt) {
        // Skip when there is no repo or the message already exists.
        if (repository == null || repository.findById(id).isPresent()) {
            return;
        }
        CaseMessage msg = new CaseMessage();
        msg.setId(id);
        msg.setClaimId(claimId);
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setMessage(message);
        msg.setCreatedAt(createdAt);
        msg.setIsRead(false); // seeded messages start unread
        repository.save(msg);
    }

    /**
     * Core seed routine shared by both {@code seedData} overloads. Returns the
     * {@link CommandLineRunner} that, when seeding is enabled, performs the full
     * idempotent seed: always upserts demo users and default system settings, then
     * either tops up "realistic activity" (if the database already has found items)
     * or performs the complete first-time seed (zones, assets, items, lost reports,
     * claims, recovery cases, passes, relay nodes, custody ledger, notifications,
     * deliveries, audit log, and a couple of demo users).
     *
     * <p>All database work is wrapped in a try/catch that logs and skips on
     * {@link DataAccessException} so an unavailable MongoDB never crashes startup.
     * Optional collaborators may be null (see the test overload) and the called
     * helpers guard against that.</p>
     *
     * @param foundItems            found-item repository
     * @param lostReports           lost-report repository
     * @param claims                claim repository
     * @param notifications         notification repository
     * @param auditLogs             audit-log repository
     * @param users                 app-user repository
     * @param campusZones           campus-zone repository (nullable)
     * @param assetRecords          asset-registry repository (nullable)
     * @param recoveryCases         recovery-case repository (nullable)
     * @param returnPasses          return-pass repository (nullable)
     * @param preventionAlerts      prevention-alert repository (nullable; unused here)
     * @param notificationDeliveries notification-delivery repository (nullable)
     * @param recoveryNodes         recovery-node repository (nullable)
     * @param custodyLedgerService  custody-ledger service (nullable)
     * @param systemSettings        system-settings service (nullable)
     * @param pickupLocation        default pickup location for system settings
     * @param pickupHours           default pickup hours for system settings
     * @param seedEnabled           master switch; when false the runner does nothing
     * @return the seed runner
     */
    private CommandLineRunner seedDataRunner(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            CampusZoneRepository campusZones,
            AssetRegistryRecordRepository assetRecords,
            RecoveryCaseRepository recoveryCases,
            ReturnPassRepository returnPasses,
            PreventionAlertRepository preventionAlerts,
            NotificationDeliveryRepository notificationDeliveries,
            RecoveryNodeRepository recoveryNodes,
            CustodyLedgerService custodyLedgerService,
            SystemSettingService systemSettings,
            String pickupLocation,
            String pickupHours,
            boolean seedEnabled
    ) {
        return args -> {
            if (!seedEnabled) {
                return;
            }

            try {
                // Always upsert the four required demo accounts so they exist on
                // every startup, even when the DB was already seeded on a prior run.
                upsertDemoUsers(users);

                // Always seed default system settings (idempotent)
                if (systemSettings != null) {
                    systemSettings.seedIfAbsent("categories",
                            "[\"electronics\",\"clothing\",\"bags_cases\",\"personal_items\"," +
                            "\"food_containers\",\"books_stationery\",\"keys\",\"jewelry\"," +
                            "\"sports_equipment\",\"musical_instruments\",\"other\"]");
                    systemSettings.seedIfAbsent("pickup.location",
                            pickupLocation != null ? pickupLocation : "PVHS Main Office pickup station");
                    systemSettings.seedIfAbsent("pickup.hours",
                            pickupHours != null ? pickupHours : "School days, 8:00 AM-3:30 PM");
                }

                // If the DB is already populated, only top up the insert-if-absent
                // "realistic activity" records, then stop (skip the first-time seed).
                if (foundItems.count() > 0) {
                    seedRealisticActivity(foundItems, lostReports, claims, notifications);
                    return;
                }

                // First-time seed of an empty database: build the full demo dataset in order.
                seedZones(campusZones); // campus recovery zones
                seedAssets(assetRecords); // school asset-registry records
                seedItems(foundItems); // core found items
                seedLostReports(lostReports); // matching lost reports
                seedClaims(claims); // claims against found items
                seedRecoveryCases(recoveryCases); // recovery case(s)
                seedPasses(returnPasses); // return passes (pickup credentials)
                seedRelay(recoveryNodes); // recovery relay partner nodes
                seedCustody(custodyLedgerService); // append-only chain-of-custody events
                seedRealisticActivity(foundItems, lostReports, claims, notifications); // extra realistic items/reports/claims/notifs

                // Two standalone demo notifications for the dashboard/admin views.
                notifications.save(notification("notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "Return Pass ready", "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions.", "return_pass_ready", "/return-pass/pass_calculator_active", "found_claimed_calculator"));
                notifications.save(notification("notif_pattern_review_demo", "avery.patel@pleasantvalley.edu", "Pattern Review alert", "A loss pattern needs admin review.", "pattern_review_alert", "/admin/pattern-review", "alert_demo_pattern"));
                seedNotificationDeliveries(notificationDeliveries); // mock multi-channel delivery records
                auditLogs.save(auditLog()); // single audit-log entry marking the seed
                // Two extra student accounts referenced by seeded claims/reports.
                users.save(user("user_001", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "student"));
                users.save(user("user_003", "Riley Chen", "riley.chen@pleasantvalley.edu", "student"));
            } catch (DataAccessException exception) {
                // Database unavailable: warn and continue rather than failing startup.
                LOGGER.warn("Skipping seed data because MongoDB is unavailable: {}", exception.getMessage());
            }
        };
    }

    /**
     * Seeds the fixed set of named campus recovery zones (library, gym, cafeteria,
     * etc.) that items and reports reference by id.
     *
     * @param campusZones campus-zone repository; no-op when null
     */
    private void seedZones(CampusZoneRepository campusZones) {
        if (campusZones == null) {
            return;
        }
        // Persist all zones in one batch.
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

    /**
     * Seeds the school's asset-registry records (tagged property such as
     * Chromebooks, library books, cameras, instruments) that found items can be
     * linked to via their asset tag / record id.
     *
     * @param assetRecords asset-registry repository; no-op when null
     */
    private void seedAssets(AssetRegistryRecordRepository assetRecords) {
        if (assetRecords == null) {
            return;
        }
        // Persist all asset records in one batch.
        assetRecords.saveAll(List.of(
                asset("asset_cb_1042", "PVHS-CB-1042", "Chromebook", "Technology Office"),
                asset("asset_book_8821", "LIB-BOOK-8821", "Library Book", "Library Return Desk"),
                asset("asset_cam_027", "ATH-CAM-027", "Camera", "Athletics Office"),
                asset("asset_band_008", "BAND-INST-008", "Instrument", "Fine Arts Office")
        ));
    }

    /**
     * Seeds the core set of found items for an empty database — a mix of statuses
     * (FOUND, VERIFIED, CLAIM_PENDING, ARCHIVED), event-hub/zone links, asset-tagged
     * and restricted-visibility items, and items carrying private verification clues
     * and storage locations. Each {@code FoundItem} is built via {@link #foundItem}
     * and then enriched with extra fields before a single batch save.
     *
     * @param foundItems found-item repository to populate
     */
    private void seedItems(FoundItemRepository foundItems) {
        // Matte black Hydro Flask — simple FOUND item with finder/staff metadata.
        FoundItem bottle = foundItem("found_001", "Black Hydro Flask Water Bottle", "food_containers", "Matte black Hydro Flask bottle with a top carry handle and screw cap.", "Black", "Hydro Flask", "Gymnasium", "2026-03-11", "12:15", ItemStatus.FOUND, "FB-2026-HF82");
        bottle.setPhotoUrls(List.of("/items/black-hydro-flask.jpg"));
        bottle.setTags(List.of("water bottle", "hydro flask", "black", "gym"));
        bottle.setStorageLocation("Main Office shelf B2");
        bottle.setFinderName("Coach Miller");
        bottle.setFinderEmail("coach.miller@pleasantvalley.edu");
        bottle.setFinderRole("staff");

        // found_002 (Blue JanSport Backpack) lives in seedRealisticActivity instead,
        // so it is also added to databases that were already seeded by an older build.

        // Black AirPods case from the basketball-game event hub; carries sealed clues.
        FoundItem airpods = foundItem("found_airpods_game", "Black AirPods-style Case", "electronics", "Black wireless earbud case found after the basketball game.", "Black", "Apple", "Gym Bleachers", "2026-03-14", "20:40", ItemStatus.FOUND, "FB-2026-AP14");
        airpods.setEventHubId("hub_basketball_game");
        airpods.setCampusZoneId("zone_gym_bleachers");
        airpods.setPhotoUrls(List.of("/items/airpods-pro-case.png"));
        airpods.setTags(List.of("airpods", "black", "earbuds", "gym bleachers"));
        airpods.setPrivateVerificationClues(List.of("small silver initials on the hinge", "tiny scratch along the left back corner"));
        airpods.setStorageLocation("Main Office sealed bin A1");

        // Silver TI calculator (FOUND) from the gym entrance, with private clues.
        FoundItem calculator = foundItem("found_calculator_demo", "Silver Graphing Calculator", "electronics", "Silver graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.FOUND, "FB-2026-CAL55");
        calculator.setEventHubId("hub_basketball_game");
        calculator.setCampusZoneId("zone_gym_entrance");
        calculator.setPhotoUrls(List.of("/items/ti-calculator.png"));
        calculator.setPrivateVerificationClues(List.of("name label under the slide cover", "faint star sticker on the back"));
        calculator.setTags(List.of("calculator", "silver", "texas instruments", "gym entrance"));
        calculator.setStorageLocation("Main Office sealed bin A2");

        // VERIFIED calculator backing the active demo Return Pass (pass_calculator_active).
        FoundItem passItem = foundItem("found_claimed_calculator", "Silver Graphing Calculator", "electronics", "TI-style graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.VERIFIED, "FB-2026-CAL77");
        passItem.setEventHubId("hub_basketball_game");
        passItem.setCampusZoneId("zone_gym_entrance");
        passItem.setPrivateVerificationClues(List.of("name label under the slide cover"));
        passItem.setPhotoUrls(List.of("/items/ti-calculator.png"));
        passItem.setStorageLocation("Main Office pickup drawer");

        // Already-returned lanyard (ARCHIVED), marked claim-confirmed — completed case example.
        FoundItem returned = foundItem("found_returned_lanyard", "PVHS Lanyard With Keys", "personal_items", "Blue PVHS lanyard with two keys.", "Blue", "PVHS", "Athletics Office", "2026-03-13", "17:20", ItemStatus.ARCHIVED, "FB-2026-KEY22");
        returned.setEventHubId("hub_basketball_game");
        returned.setCampusZoneId("zone_athletics");
        returned.setPhotoUrls(List.of("/items/pvhs-lanyard.png"));
        returned.setClaimConfirmed(true);
        returned.setClaimConfirmedAt("2026-03-14T16:30:00Z");

        // Asset-tagged, restricted-visibility school Chromebook routed to the Tech Office.
        FoundItem chromebook = foundItem("found_asset_chromebook", "PVHS Chromebook", "electronics", "School-owned Chromebook with asset tag.", "Gray", "Lenovo", "Library Study Area", "2026-03-12", "11:05", ItemStatus.FOUND, "FB-2026-CB1042");
        chromebook.setAssetTag("PVHS-CB-1042");
        chromebook.setAssetRecordId("asset_cb_1042");
        chromebook.setDepartmentDestination("Technology Office");
        chromebook.setRestrictedVisibility(true);
        chromebook.setPhotoUrls(List.of("/images/locker-cool.png"));
        chromebook.setStorageLocation("Technology Office intake shelf");

        // Plain clothing item (no brand) found in the cafeteria.
        FoundItem hoodie = foundItem("found_blue_hoodie", "Blue Zip-Up Hoodie", "clothing", "Blue zip-up hoodie with a school logo on the left chest.", "Blue", "", "Cafeteria", "2026-03-15", "12:30", ItemStatus.FOUND, "FB-2026-HDY01");
        hoodie.setCampusZoneId("zone_cafeteria");
        hoodie.setPhotoUrls(List.of("/items/nike-hoodie.png"));
        hoodie.setTags(List.of("hoodie", "blue", "zip-up", "clothing"));
        hoodie.setStorageLocation("Main Office clothing bin");

        // Compact umbrella found in the library study area.
        FoundItem umbrella = foundItem("found_red_umbrella", "Red Compact Umbrella", "personal_items", "Small red compact umbrella found in the library study area.", "Red", "", "Library Study Area", "2026-03-15", "14:00", ItemStatus.FOUND, "FB-2026-UMB02");
        umbrella.setCampusZoneId("zone_library");
        umbrella.setPhotoUrls(List.of("/items/red-umbrella.png"));
        umbrella.setTags(List.of("umbrella", "red", "compact", "library"));
        umbrella.setStorageLocation("Main Office shelf B3");

        // Persist all core found items in a single batch.
        foundItems.saveAll(List.of(bottle, airpods, calculator, passItem, returned, chromebook, hoodie, umbrella));
    }

    /**
     * Seeds a broad set of additional "realistic activity" — extra found items
     * across many categories/zones, their matching lost reports (with match
     * suggestions), a spread of claims in various states (need_more_info, rejected,
     * pending_review, approved), Jordan's recovered backpack, and the associated
     * notifications. Everything here uses the insert-if-absent helpers
     * ({@link #saveSeeded}/{@link #saveIfMissing}) so it also lands on databases
     * that were already seeded by an older build, and runs on every startup.
     *
     * @param foundItems    found-item repository
     * @param lostReports   lost-report repository
     * @param claims        claim repository
     * @param notifications notification repository
     */
    private void seedRealisticActivity(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications
    ) {
        // ---- Additional found items across categories/zones (built then saved-if-absent below) ----
        FoundItem charger = foundItem("found_usbc_charger", "White USB-C Charger", "electronics", "White USB-C wall charger and cable found beside the cafeteria charging rail.", "White", "Apple", "Cafeteria", "2026-03-16", "13:10", ItemStatus.FOUND, "FB-2026-USBC");
        charger.setCampusZoneId("zone_cafeteria");
        charger.setPhotoUrls(List.of("/items/usbc-charger.png"));
        charger.setTags(List.of("charger", "usb-c", "white", "cafeteria"));
        charger.setPrivateVerificationClues(List.of("small blue tape mark near the plug", "short six-foot cable included"));
        charger.setStorageLocation("Main Office electronics drawer");
        charger.setFinderName("Nora Lee");
        charger.setFinderEmail("nora.lee@pleasantvalley.edu");
        charger.setFinderRole("student");

        FoundItem textbook = foundItem("found_ap_biology_textbook", "AP Biology Textbook", "books_stationery", "Campbell AP Biology textbook with a PVHS library barcode.", "Green", "Pearson", "Library Study Area", "2026-03-16", "09:45", ItemStatus.FOUND, "FB-2026-BIO");
        textbook.setCampusZoneId("zone_library");
        textbook.setAssetTag("LIB-BOOK-8821");
        textbook.setAssetRecordId("asset_book_8821");
        textbook.setDepartmentDestination("Library Return Desk");
        textbook.setPhotoUrls(List.of("/items/ap-biology-textbook.png"));
        textbook.setTags(List.of("textbook", "biology", "library", "green"));
        textbook.setStorageLocation("Library return desk");
        textbook.setFinderName("Ms. Harper");
        textbook.setFinderEmail("harper@pleasantvalley.edu");
        textbook.setFinderRole("staff");

        FoundItem kneepads = foundItem("found_volleyball_kneepads", "Black Volleyball Knee Pads", "sports_equipment", "Pair of black volleyball knee pads found after practice.", "Black", "Nike", "Gym Bleachers", "2026-03-16", "17:35", ItemStatus.FOUND, "FB-2026-KNEE");
        kneepads.setCampusZoneId("zone_gym_bleachers");
        kneepads.setPhotoUrls(List.of("/items/volleyball-kneepads.png"));
        kneepads.setTags(List.of("volleyball", "knee pads", "black", "gym"));
        kneepads.setStorageLocation("Athletics Office bin");
        kneepads.setFinderName("Coach Miller");
        kneepads.setFinderEmail("coach.miller@pleasantvalley.edu");
        kneepads.setFinderRole("staff");

        FoundItem pencilCase = foundItem("found_pink_pencil_case", "Pink Pencil Case", "books_stationery", "Pink zipper pencil case with highlighters and mechanical pencils.", "Pink", "", "Auditorium", "2026-03-17", "10:20", ItemStatus.FOUND, "FB-2026-PENC");
        pencilCase.setCampusZoneId("zone_auditorium");
        pencilCase.setPhotoUrls(List.of("/items/pink-pencil-case.png"));
        pencilCase.setTags(List.of("pencil case", "pink", "auditorium", "stationery"));
        pencilCase.setStorageLocation("Main Office shelf C1");
        pencilCase.setFinderName("Evan Brooks");
        pencilCase.setFinderEmail("evan.brooks@pleasantvalley.edu");
        pencilCase.setFinderRole("student");

        FoundItem sunglasses = foundItem("found_rayban_sunglasses", "Black Sunglasses", "personal_items", "Black sunglasses in a soft case found near the bus loop.", "Black", "Ray-Ban", "Bus Loop", "2026-03-17", "15:45", ItemStatus.VERIFIED, "FB-2026-SUN");
        sunglasses.setCampusZoneId("zone_bus_loop");
        sunglasses.setPhotoUrls(List.of("/items/rayban-sunglasses.png"));
        sunglasses.setTags(List.of("sunglasses", "black", "bus loop", "case"));
        sunglasses.setStorageLocation("Main Office shelf A4");
        sunglasses.setFinderName("Transportation Desk");
        sunglasses.setFinderEmail("transportation@pleasantvalley.edu");
        sunglasses.setFinderRole("staff");

        FoundItem watch = foundItem("found_casio_watch", "Black Digital Watch", "jewelry", "Black digital watch found on the auditorium stage steps.", "Black", "Casio", "Auditorium", "2026-03-17", "18:05", ItemStatus.FOUND, "FB-2026-WTCH");
        watch.setCampusZoneId("zone_auditorium");
        watch.setPhotoUrls(List.of("/items/casio-watch.png"));
        watch.setTags(List.of("watch", "casio", "black", "auditorium"));
        watch.setPrivateVerificationClues(List.of("alarm is set for 6:45 AM", "small nick on the lower strap"));
        watch.setStorageLocation("Main Office valuables pouch");
        watch.setFinderName("Stage Crew");
        watch.setFinderEmail("stagecrew@pleasantvalley.edu");
        watch.setFinderRole("student");

        FoundItem lunchBag = foundItem("found_lunch_bag_floral", "Navy Floral Lunch Bag", "food_containers", "Navy insulated lunch bag with a white floral pattern found after lunch.", "Navy", "Bentgo", "Cafeteria", "2026-03-12", "12:55", ItemStatus.CLAIM_PENDING, "FB-2026-LB33");
        lunchBag.setCampusZoneId("zone_cafeteria");
        lunchBag.setTags(List.of("lunch bag", "navy", "floral", "cafeteria"));
        lunchBag.setPhotoUrls(List.of("/items/floral-lunch-bag.png"));
        lunchBag.setPrivateVerificationClues(List.of("inside name patch", "small strawberry keychain on zipper"));
        lunchBag.setStorageLocation("Main Office food-safe shelf");
        lunchBag.setFinderName("Ms. Greene");
        lunchBag.setFinderEmail("ms.greene@pleasantvalley.edu");
        lunchBag.setFinderRole("staff");

        FoundItem debateFolder = foundItem("found_debate_folder", "Green Debate Folder", "books_stationery", "Green plastic folder with loose debate notes found near the auditorium doors.", "Green", "", "Auditorium", "2026-03-13", "16:10", ItemStatus.CLAIM_PENDING, "FB-2026-DF41");
        debateFolder.setCampusZoneId("zone_auditorium");
        debateFolder.setTags(List.of("folder", "debate", "green", "auditorium"));
        debateFolder.setPhotoUrls(List.of("/items/green-folder.png"));
        debateFolder.setPrivateVerificationClues(List.of("handwritten tournament schedule on the inside pocket", "orange sticky note on first page"));
        debateFolder.setStorageLocation("Main Office document tray");

        FoundItem soccerCleats = foundItem("found_soccer_cleats", "Black Adidas Soccer Cleats", "sports_equipment", "Pair of black Adidas soccer cleats in a drawstring bag.", "Black", "Adidas", "Bus Loop", "2026-03-12", "15:45", ItemStatus.FOUND, "FB-2026-SOC18");
        soccerCleats.setCampusZoneId("zone_bus_loop");
        soccerCleats.setTags(List.of("soccer", "cleats", "black", "bus loop"));
        soccerCleats.setPhotoUrls(List.of("/items/soccer-cleats.png"));
        soccerCleats.setPrivateVerificationClues(List.of("number written inside both heels", "one orange lace tip"));
        soccerCleats.setStorageLocation("Athletics Office equipment shelf");

        FoundItem pearlEarring = foundItem("found_pearl_earring", "Single Pearl Earring", "jewelry", "Single pearl-style stud earring turned in after the spring choir concert.", "White", "", "Auditorium", "2026-03-11", "21:05", ItemStatus.CLAIM_PENDING, "FB-2026-JWL09");
        pearlEarring.setCampusZoneId("zone_auditorium");
        pearlEarring.setTags(List.of("earring", "pearl", "jewelry", "auditorium"));
        pearlEarring.setPhotoUrls(List.of("/items/pearl-earring.png"));
        pearlEarring.setPrivateVerificationClues(List.of("gold-toned back", "matching earring has a tiny flat spot"));
        pearlEarring.setStorageLocation("Main Office small-items envelope");

        FoundItem mouthpiece = foundItem("found_clarinet_mouthpiece", "Clarinet Mouthpiece Case", "musical_instruments", "Black clarinet mouthpiece case found outside the band room.", "Black", "Vandoren", "Fine Arts Hallway", "2026-03-10", "14:20", ItemStatus.FOUND, "FB-2026-MUS16");
        mouthpiece.setTags(List.of("clarinet", "mouthpiece", "band", "fine arts"));
        mouthpiece.setPhotoUrls(List.of("/items/clarinet-mouthpiece.png"));
        mouthpiece.setPrivateVerificationClues(List.of("blue tape on the side", "reed strength written on label"));
        mouthpiece.setStorageLocation("Fine Arts Office lost-and-found drawer");

        // Insert each additional item only if absent (or if it is an existing demo record).
        saveSeeded(foundItems, charger);
        saveSeeded(foundItems, textbook);
        saveSeeded(foundItems, kneepads);
        saveSeeded(foundItems, pencilCase);
        saveSeeded(foundItems, sunglasses);
        saveSeeded(foundItems, watch);
        saveSeeded(foundItems, lunchBag);
        saveSeeded(foundItems, debateFolder);
        saveSeeded(foundItems, soccerCleats);
        saveSeeded(foundItems, pearlEarring);
        saveSeeded(foundItems, mouthpiece);

        // Jordan's recovered backpack (claimed via claim_001). Kept here in the
        // insert-if-absent path so it also lands on already-seeded databases.
        FoundItem backpack = foundItem("found_002", "Blue JanSport Backpack", "bags_cases", "Royal blue JanSport backpack with math notebook and tennis keychain.", "Blue", "JanSport", "Student Lounge", "2026-03-09", "15:05", ItemStatus.VERIFIED, "FB-2026-JS27");
        backpack.setPhotoUrls(List.of("/images/blue-backpack.png"));
        backpack.setTags(List.of("backpack", "jansport", "blue", "student lounge"));
        backpack.setStorageLocation("Counselor office storage closet");
        saveSeeded(foundItems, backpack);

        // ---- Matching lost reports, each carrying a seeded match suggestion to its found item ----
        LostReport chargerReport = lostReport("lost_usbc_charger", "Lost white USB-C charger", "electronics", "White USB-C charger and cable left near the cafeteria charging rail.", "White", "Apple", "Cafeteria", "2026-03-16", "sophia.nguyen@pleasantvalley.edu");
        chargerReport.setContactName("Sophia Nguyen");
        chargerReport.setCampusZoneId("zone_cafeteria");
        chargerReport.setPhotoUrls(List.of("/items/usbc-charger.png"));
        chargerReport.setMatchedItems(List.of(match("found_usbc_charger", "White USB-C Charger", 91)));
        saveSeeded(lostReports, chargerReport);

        LostReport textbookReport = lostReport("lost_ap_biology_textbook", "Lost AP Biology textbook", "books_stationery", "Green AP Biology textbook last used during library study hall.", "Green", "Pearson", "Library Study Area", "2026-03-16", "jordan.kim@pleasantvalley.edu");
        textbookReport.setContactName("Jordan Kim");
        textbookReport.setCampusZoneId("zone_library");
        textbookReport.setPhotoUrls(List.of("/items/ap-biology-textbook.png"));
        textbookReport.setMatchedItems(List.of(match("found_ap_biology_textbook", "AP Biology Textbook", 89)));
        saveSeeded(lostReports, textbookReport);

        LostReport sunglassesReport = lostReport("lost_black_sunglasses", "Lost black sunglasses", "personal_items", "Black sunglasses in a soft case, possibly left near the bus loop.", "Black", "Ray-Ban", "Bus Loop", "2026-03-17", "mia.rodriguez@pleasantvalley.edu");
        sunglassesReport.setContactName("Mia Rodriguez");
        sunglassesReport.setCampusZoneId("zone_bus_loop");
        sunglassesReport.setPhotoUrls(List.of("/items/rayban-sunglasses.png"));
        sunglassesReport.setMatchedItems(List.of(match("found_rayban_sunglasses", "Black Sunglasses", 86)));
        saveSeeded(lostReports, sunglassesReport);

        LostReport lunchBagReport = lostReport("lost_lunch_bag_sophia", "Lost navy floral lunch bag", "food_containers", "Navy lunch bag with a white floral print and zipper charm.", "Navy", "Bentgo", "Cafeteria", "2026-03-12", "sophia.nguyen@pleasantvalley.edu");
        lunchBagReport.setContactName("Sophia Nguyen");
        lunchBagReport.setCampusZoneId("zone_cafeteria");
        lunchBagReport.setTimeLost("12:30");
        lunchBagReport.setMatchedItems(List.of(match("found_lunch_bag_floral", "Navy Floral Lunch Bag", 91)));
        saveSeeded(lostReports, lunchBagReport);

        LostReport debateFolderReport = lostReport("lost_debate_folder", "Lost green debate folder", "books_stationery", "Green debate folder with practice notes from tournament prep.", "Green", "", "Auditorium", "2026-03-13", "emma.wilson@pleasantvalley.edu");
        debateFolderReport.setContactName("Emma Wilson");
        debateFolderReport.setCampusZoneId("zone_auditorium");
        debateFolderReport.setUrgency("high");
        debateFolderReport.setTimeLost("15:55");
        debateFolderReport.setMatchedItems(List.of(match("found_debate_folder", "Green Debate Folder", 89)));
        saveSeeded(lostReports, debateFolderReport);

        LostReport soccerCleatsReport = lostReport("lost_soccer_cleats", "Missing black soccer cleats", "sports_equipment", "Black Adidas soccer cleats left after away-game bus unloading.", "Black", "Adidas", "Bus Loop", "2026-03-12", "marcus.johnson@pleasantvalley.edu");
        soccerCleatsReport.setContactName("Marcus Johnson");
        soccerCleatsReport.setCampusZoneId("zone_bus_loop");
        soccerCleatsReport.setTimeLost("15:30");
        soccerCleatsReport.setMatchedItems(List.of(match("found_soccer_cleats", "Black Adidas Soccer Cleats", 84)));
        saveSeeded(lostReports, soccerCleatsReport);

        LostReport earringReport = lostReport("lost_pearl_earring", "Lost pearl earring", "jewelry", "One pearl-style stud earring missing after choir concert.", "White", "", "Auditorium", "2026-03-11", "hannah.lee@pleasantvalley.edu");
        earringReport.setContactName("Hannah Lee");
        earringReport.setCampusZoneId("zone_auditorium");
        earringReport.setTimeLost("20:50");
        earringReport.setMatchedItems(List.of(match("found_pearl_earring", "Single Pearl Earring", 82)));
        saveSeeded(lostReports, earringReport);

        LostReport mouthpieceReport = lostReport("lost_clarinet_mouthpiece", "Lost clarinet mouthpiece case", "musical_instruments", "Black Vandoren mouthpiece case misplaced after band rehearsal.", "Black", "Vandoren", "Fine Arts Hallway", "2026-03-10", "noah.anderson@pleasantvalley.edu");
        mouthpieceReport.setContactName("Noah Anderson");
        mouthpieceReport.setTimeLost("14:05");
        mouthpieceReport.setMatchedItems(List.of(match("found_clarinet_mouthpiece", "Clarinet Mouthpiece Case", 86)));
        saveSeeded(lostReports, mouthpieceReport);

        // Lost report with no matching found item yet (demonstrates the "no match" state).
        LostReport rainJacketReport = lostReport("lost_black_rain_jacket", "Missing black rain jacket", "clothing", "Black lightweight rain jacket last seen by the bus loop.", "Black", "Columbia", "Bus Loop", "2026-03-15", "olivia.brown@pleasantvalley.edu");
        rainJacketReport.setContactName("Olivia Brown");
        rainJacketReport.setCampusZoneId("zone_bus_loop");
        rainJacketReport.setTimeLost("15:25");
        rainJacketReport.setExtraNotes("No matching found item yet; student checked advisory and locker.");
        saveSeeded(lostReports, rainJacketReport);

        // ---- Claims spanning the workflow states (need_more_info, rejected, pending_review, approved) ----
        Claim textbookClaim = claim("claim_textbook_needs_info", "found_ap_biology_textbook", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "need_more_info");
        textbookClaim.setFoundItemTitle("AP Biology Textbook");
        textbookClaim.setAdminNotes("Please confirm the library barcode or teacher name written inside the cover.");
        textbookClaim.setClaimReason("This looks like my AP Biology book from fourth period study hall.");
        textbookClaim.setIdentifyingDetails("The first page should have my last name and a sticky note from unit 7.");
        saveSeeded(claims, textbookClaim);

        Claim sunglassesClaim = claim("claim_sunglasses_rejected", "found_rayban_sunglasses", "Mia Rodriguez", "mia.rodriguez@pleasantvalley.edu", "rejected");
        sunglassesClaim.setFoundItemTitle("Black Sunglasses");
        sunglassesClaim.setAdminNotes("Claim details did not match the case and frame description.");
        sunglassesClaim.setClaimReason("I lost black sunglasses after school near the bus loop.");
        sunglassesClaim.setIdentifyingDetails("Mine did not have a case, so these may not be mine.");
        saveSeeded(claims, sunglassesClaim);

        Claim lunchReview = claim("claim_lunch_bag_review", "found_lunch_bag_floral", "Sophia Nguyen", "sophia.nguyen@pleasantvalley.edu", "pending_review");
        lunchReview.setFoundItemTitle("Navy Floral Lunch Bag");
        lunchReview.setClaimReason("I left my lunch bag on the cafeteria table after fifth lunch.");
        lunchReview.setIdentifyingDetails("It has my name patch inside and a strawberry charm on the zipper.");
        lunchReview.setEvidenceChecklist(List.of("name patch", "zipper charm", "last known cafeteria table"));
        lunchReview.setPrivateEvidenceResponses(java.util.Map.of("name_patch", "Sophia name patch", "zipper_charm", "strawberry charm"));
        lunchReview.setVerificationScore(91);
        lunchReview.setVerificationFlags(List.of("specific hidden detail", "same lunch period"));
        lunchReview.setVerificationSummary("Claim matches hidden intake details and the reported cafeteria time.");
        saveSeeded(claims, lunchReview);

        Claim debateNeedsInfo = claim("claim_debate_more_info", "found_debate_folder", "Emma Wilson", "emma.wilson@pleasantvalley.edu", "need_more_info");
        debateNeedsInfo.setFoundItemTitle("Green Debate Folder");
        debateNeedsInfo.setClaimReason("This should be my folder from debate practice.");
        debateNeedsInfo.setIdentifyingDetails("It has my tournament schedule inside.");
        debateNeedsInfo.setAdminNotes("Ask claimant to identify the sticky note color before approval.");
        debateNeedsInfo.setReviewedBy("avery.patel@pleasantvalley.edu");
        debateNeedsInfo.setReviewedAt("2026-03-14T09:20:00Z");
        debateNeedsInfo.setEvidenceChecklist(List.of("inside pocket contents", "sticky note color"));
        debateNeedsInfo.setVerificationScore(72);
        debateNeedsInfo.setVerificationFlags(List.of("partial detail match", "needs one more private detail"));
        debateNeedsInfo.setVerificationSummary("The folder details overlap, but one private verification clue is still missing.");
        saveSeeded(claims, debateNeedsInfo);

        Claim earringReview = claim("claim_pearl_earring_review", "found_pearl_earring", "Hannah Lee", "hannah.lee@pleasantvalley.edu", "pending_review");
        earringReview.setFoundItemTitle("Single Pearl Earring");
        earringReview.setClaimReason("I lost one earring after choir concert cleanup.");
        earringReview.setIdentifyingDetails("The backing is gold-toned, and the matching earring has a tiny flat spot.");
        earringReview.setEvidenceChecklist(List.of("matching item", "backing color", "concert location"));
        earringReview.setVerificationScore(86);
        earringReview.setVerificationFlags(List.of("specific matching-pair detail"));
        earringReview.setVerificationSummary("Claim gives a specific matching-pair detail suitable for staff review.");
        saveSeeded(claims, earringReview);

        Claim soccerRejected = claim("claim_soccer_cleats_rejected", "found_soccer_cleats", "Tyler Reed", "tyler.reed@pleasantvalley.edu", "rejected");
        soccerRejected.setFoundItemTitle("Black Adidas Soccer Cleats");
        soccerRejected.setClaimReason("I lost black cleats near the bus loop.");
        soccerRejected.setIdentifyingDetails("They are size 9 with blue laces.");
        soccerRejected.setAdminNotes("Rejected because private details did not match the sealed intake clues.");
        soccerRejected.setReviewedBy("avery.patel@pleasantvalley.edu");
        soccerRejected.setReviewedAt("2026-03-13T10:15:00Z");
        soccerRejected.setRiskScore(35);
        soccerRejected.setRiskFlags(List.of("details conflict with intake clues", "demo record"));
        saveSeeded(claims, soccerRejected);

        // Jordan's approved backpack claim. The title is carried on the claim so the
        // claimant dashboard renders the card even though the now-VERIFIED item is
        // hidden from the public item endpoint.
        Claim approvedBackpack = claim("claim_001", "found_002", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "approved");
        approvedBackpack.setFoundItemTitle("Blue JanSport Backpack");
        saveSeeded(claims, approvedBackpack);

        // ---- Notifications for the above activity, inserted only if not already present ----
        saveIfMissing(notifications, notification("notif_charger_match", "sophia.nguyen@pleasantvalley.edu", "Possible charger match", "A white USB-C charger may match your lost report.", "strong_item_match", "/UserDashboard", "found_usbc_charger"));
        saveIfMissing(notifications, notification("notif_textbook_info", "jordan.kim@pleasantvalley.edu", "More info needed", "Staff requested one more detail for your AP Biology textbook claim.", "claim_more_info", "/claims/claim_textbook_needs_info", "found_ap_biology_textbook"));
        saveIfMissing(notifications, notification("notif_sunglasses_reviewed", "mia.rodriguez@pleasantvalley.edu", "Claim reviewed", "Staff reviewed the sunglasses claim and left a decision.", "claim_reviewed", "/claims/claim_sunglasses_rejected", "found_rayban_sunglasses"));
        saveIfMissing(notifications, notification("notif_lunch_review", "sophia.nguyen@pleasantvalley.edu", "Claim under review", "Staff are reviewing your lunch bag claim against private intake details.", "claim_review", "/claim?item=found_lunch_bag_floral", "found_lunch_bag_floral"));
        saveIfMissing(notifications, notification("notif_debate_more_info", "emma.wilson@pleasantvalley.edu", "More info needed", "Staff need one more private detail before approving the debate folder claim.", "claim_more_info_requested", "/claim?item=found_debate_folder", "found_debate_folder"));
        saveIfMissing(notifications, notification("notif_clarinet_match", "noah.anderson@pleasantvalley.edu", "Possible match found", "A clarinet mouthpiece case may match your lost report.", "strong_item_match", "/browse", "found_clarinet_mouthpiece"));
        saveIfMissing(notifications, notification("notif_earring_review", "hannah.lee@pleasantvalley.edu", "Jewelry claim received", "Your earring claim is queued for staff review.", "claim_review", "/claim?item=found_pearl_earring", "found_pearl_earring"));
        saveIfMissing(notifications, notification("notif_001", "jordan.kim@pleasantvalley.edu", "Strong match available", "A strong possible match is ready for review.", "strong_item_match", "/UserDashboard", "found_002"));
    }

    /**
     * Seeds the first-time lost reports: Jordan's blue backpack (matched to
     * found_002) and two basketball-game event reports (AirPods case and calculator)
     * linked to the event hub and gym zones with high-confidence match suggestions.
     *
     * @param lostReports lost-report repository to populate
     */
    private void seedLostReports(LostReportRepository lostReports) {
        // Blue backpack report matched to Jordan's recovered backpack.
        LostReport report = lostReport("lost_001", "Missing blue backpack", "bags_cases", "Blue JanSport backpack with a tennis keychain.", "Blue", "JanSport", "Student Lounge", "2026-03-09", "jordan.kim@pleasantvalley.edu");
        report.setMatchedItems(List.of(match("found_002", "Blue JanSport Backpack", 96)));
        lostReports.save(report);

        // Event-hub AirPods report (high urgency) matched to found_airpods_game.
        LostReport airpods = lostReport("lost_airpods_game", "Lost black AirPods-style case", "electronics", "Black earbud case lost during the PVHS vs. Bettendorf game.", "Black", "Apple", "Gym Bleachers", "2026-03-14", "mia.rodriguez@pleasantvalley.edu");
        airpods.setEventHubId("hub_basketball_game");
        airpods.setCampusZoneId("zone_gym_bleachers");
        airpods.setUrgency("high");
        airpods.setMatchedItems(List.of(match("found_airpods_game", "Black AirPods-style Case", 94)));
        lostReports.save(airpods);

        // Event-hub calculator report (high urgency) matched to found_calculator_demo.
        LostReport calculator = lostReport("lost_calculator_demo", "Lost silver graphing calculator", "electronics", "Silver Texas Instruments calculator with a name label under the slide cover.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "riley.chen@pleasantvalley.edu");
        calculator.setEventHubId("hub_basketball_game");
        calculator.setCampusZoneId("zone_gym_entrance");
        calculator.setUrgency("high");
        calculator.setMatchedItems(List.of(match("found_calculator_demo", "Silver Graphing Calculator", 97)));
        lostReports.save(calculator);
    }

    /**
     * Seeds the first-time claims tied to the event-hub items: a pending_review
     * AirPods claim (with evidence/verification details), an approved calculator
     * claim, and a completed keys/lanyard claim. Note claim_001 is intentionally
     * seeded elsewhere (see {@link #seedRealisticActivity}).
     *
     * @param claims claim repository to populate
     */
    private void seedClaims(ClaimRepository claims) {
        // claim_001 (Jordan's approved backpack claim) lives in seedRealisticActivity
        // so it also lands on already-seeded databases.
        // AirPods claim awaiting staff review, with strong evidence overlap.
        Claim review = claim("claim_airpods_review", "found_airpods_game", "Mia Rodriguez", "mia.rodriguez@pleasantvalley.edu", "pending_review");
        review.setEvidenceChecklist(List.of("hidden mark", "case condition", "last known location"));
        review.setPrivateEvidenceResponses(java.util.Map.of("hidden_mark", "silver initials on the hinge", "condition", "scratch on the back corner"));
        review.setIdentifyingDetails("It has my small silver initials and a scratch on the back left corner.");
        review.setVerificationScore(88);
        review.setVerificationFlags(List.of("strong overlap", "proof photo supplied"));
        review.setVerificationSummary("Claim evidence strongly overlaps with sealed verification clues. Staff review is still required.");
        claims.save(review);

        // Approved calculator claim that backs the active Return Pass.
        Claim approved = claim("claim_calculator_approved", "found_claimed_calculator", "Riley Chen", "riley.chen@pleasantvalley.edu", "approved");
        approved.setIdentifyingDetails("Name label under the slide cover.");
        claims.save(approved);

        // Fully completed lanyard/keys claim (item already received).
        Claim completed = claim("claim_keys_completed", "found_returned_lanyard", "Avery Brooks", "avery.brooks@pleasantvalley.edu", "completed");
        completed.setReceivedConfirmedAt("2026-03-14T16:30:00Z");
        claims.save(completed);
    }

    /**
     * Seeds a single recovery case for the AirPods event item — linking the lost
     * report, selected found item, and the in-review claim — with a scripted
     * recovery plan and likely-zone confidence summaries for the admin view.
     *
     * @param recoveryCases recovery-case repository; no-op when null
     */
    private void seedRecoveryCases(RecoveryCaseRepository recoveryCases) {
        if (recoveryCases == null) {
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
    }

    /**
     * Seeds two return passes (pickup credentials): an active one (PIN 314159) for
     * the calculator, redeemable live during the demo, and an already-redeemed one
     * for the returned lanyard, recording who redeemed it and when.
     *
     * @param returnPasses return-pass repository; no-op when null
     */
    private void seedPasses(ReturnPassRepository returnPasses) {
        if (returnPasses == null) {
            return;
        }
        // Active pass ready to redeem at pickup (one-time code 314159).
        ReturnPass active = pass("pass_calculator_active", "claim_calculator_approved", "found_claimed_calculator", "riley.chen@pleasantvalley.edu", "active", "314159");
        returnPasses.save(active);
        // Already-redeemed pass, annotated with redemption time and the staff redeemer.
        ReturnPass redeemed = pass("pass_keys_redeemed", "claim_keys_completed", "found_returned_lanyard", "avery.brooks@pleasantvalley.edu", "redeemed", "271828");
        redeemed.setRedeemedAt("2026-03-14T16:30:00Z");
        redeemed.setRedeemedBy("avery.patel@pleasantvalley.edu");
        returnPasses.save(redeemed);
    }

    /**
     * Seeds the recovery relay "nodes" — demo partner departments (Main Office,
     * Athletics, Transportation, Fine Arts) that participate in the recovery mesh.
     *
     * @param recoveryNodes recovery-node repository; no-op when null
     */
    private void seedRelay(RecoveryNodeRepository recoveryNodes) {
        if (recoveryNodes == null) {
            return;
        }
        // Persist all relay nodes in one batch.
        recoveryNodes.saveAll(List.of(
                node("node_main_office", "PVHS Main Office"),
                node("node_athletics", "PVHS Athletics"),
                node("node_transportation", "PVHS Transportation"),
                node("node_fine_arts", "PVHS Fine Arts")
        ));
    }

    /**
     * Seeds the chain-of-custody ledger for the demo items: the AirPods item's
     * intake/review/match trail, the calculator's pickup-ready event, and the
     * lanyard's handoff/return events. Because the ledger is append-only, it first
     * checks whether the AirPods item already has events and bails out if so, to
     * avoid duplicating chains when the full seed runs on a non-empty database.
     *
     * @param custodyLedgerService service used to append custody events; no-op when null
     */
    private void seedCustody(CustodyLedgerService custodyLedgerService) {
        if (custodyLedgerService == null) {
            return;
        }
        // Custody events are an append-only ledger, so guard against re-seeding to
        // avoid duplicate chains when the full seed runs on a non-empty database.
        if (!custodyLedgerService.list("found_airpods_game").isEmpty()) {
            return;
        }
        // AirPods item: intake -> reviewed (clues sealed) -> matched to lost report.
        custodyLedgerService.appendEvent("found_airpods_game", "intake_created", "coach.miller@pleasantvalley.edu", "staff", "Main Office sealed bin A1", "Event item intake created.", null);
        custodyLedgerService.appendEvent("found_airpods_game", "reviewed", "avery.patel@pleasantvalley.edu", "admin", "Main Office sealed bin A1", "Proof Vault clues sealed.", null);
        custodyLedgerService.appendEvent("found_airpods_game", "matched", "system@pvhs.demo", "system", "", "Advisory match linked to lost report.", null);
        // Calculator: pickup-ready event tied to the active Return Pass.
        custodyLedgerService.appendEvent("found_claimed_calculator", "pickup_ready", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Active Return Pass available.", null);
        // Lanyard: handoff verified then returned to the verified claimant.
        custodyLedgerService.appendEvent("found_returned_lanyard", "handoff_verified", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Manual code verified.", null);
        custodyLedgerService.appendEvent("found_returned_lanyard", "returned", "avery.patel@pleasantvalley.edu", "admin", "PVHS Main Office pickup station", "Item returned to verified claimant.", null);
    }

    /**
     * Factory that builds a {@link FoundItem} pre-filled with the common fields and
     * sensible seed defaults (record type "found", not flagged/claimed/restricted,
     * created/updated = {@link #NOW}, isDemo = true). Callers further customize the
     * returned object (photos, tags, clues, storage location, etc.).
     *
     * @param id          document id
     * @param title       item title
     * @param category    item category
     * @param description human-readable description
     * @param color       primary color
     * @param brand       brand (may be empty)
     * @param location    where it was found
     * @param date        date found (yyyy-MM-dd)
     * @param time        time found (HH:mm)
     * @param status      item status (see {@code ItemStatus})
     * @param itemCode    public item/reference code
     * @return a populated, demo-flagged FoundItem
     */
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

    /**
     * Factory that builds a {@link LostReport} with common fields and seed defaults
     * (status "open", urgency "medium", contact name "Demo Student", timestamps and
     * isDemo set). Callers may override contact name, urgency, zone, matches, etc.
     *
     * @param id          document id
     * @param title       report title
     * @param category    item category
     * @param description description of the lost item
     * @param color       primary color
     * @param brand       brand (may be empty)
     * @param location    where it was lost
     * @param date        date lost (yyyy-MM-dd)
     * @param email       contact email of the reporter
     * @return a populated, demo-flagged LostReport
     */
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

    /**
     * Factory that builds a {@link MatchSuggestion} linking a lost report to a found
     * item, with a confidence score and canned reasons/source ("seed") and a
     * "suggested" status.
     *
     * @param itemId     id of the candidate found item
     * @param title      title of the candidate found item
     * @param confidence match confidence (0-100)
     * @return a populated MatchSuggestion
     */
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

    /**
     * Factory that builds a {@link Claim} with common fields and seed defaults
     * (generic reason/details, risk score 10 with "demo record" flag, timestamps,
     * isDemo). Callers override title, reason, evidence, verification fields, etc.
     *
     * @param id     document id
     * @param itemId id of the claimed found item
     * @param name   claimant name
     * @param email  claimant email
     * @param status claim status (e.g. pending_review, approved, rejected)
     * @return a populated, demo-flagged Claim
     */
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

    /**
     * Saves a notification only if no document with its id already exists, so
     * reruns never duplicate notifications.
     *
     * @param repository   notification repository (no-op when null)
     * @param notification notification to insert if absent
     */
    private void saveIfMissing(NotificationRepository repository, Notification notification) {
        if (repository != null && repository.findById(notification.getId()).isEmpty()) {
            repository.save(notification);
        }
    }

    /**
     * Idempotent upsert for a seeded found item: saves when no document exists for
     * the id, or when the existing document is itself a demo record (isDemo true).
     * Real (non-demo) records with the same id are never overwritten.
     *
     * @param repository found-item repository (no-op when null)
     * @param item       the seed item to save
     */
    private void saveSeeded(FoundItemRepository repository, FoundItem item) {
        if (repository == null) {
            return;
        }
        FoundItem existing = repository.findById(item.getId()).orElse(null);
        // Insert if missing, or overwrite only when the existing record is a demo one.
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(item);
        }
    }

    /**
     * Idempotent upsert for a seeded lost report; mirrors {@link #saveSeeded(FoundItemRepository, FoundItem)}:
     * inserts when absent or only overwrites existing demo records.
     *
     * @param repository lost-report repository (no-op when null)
     * @param report     the seed report to save
     */
    private void saveSeeded(LostReportRepository repository, LostReport report) {
        if (repository == null) {
            return;
        }
        LostReport existing = repository.findById(report.getId()).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(report);
        }
    }

    /**
     * Idempotent upsert for a seeded claim; mirrors the other {@code saveSeeded}
     * overloads: inserts when absent or only overwrites existing demo records.
     *
     * @param repository claim repository (no-op when null)
     * @param claim      the seed claim to save
     */
    private void saveSeeded(ClaimRepository repository, Claim claim) {
        if (repository == null) {
            return;
        }
        Claim existing = repository.findById(claim.getId()).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(claim);
        }
    }

    /**
     * Factory that builds a {@link ReturnPass} (pickup credential) with seed
     * defaults: a generic pickup window/location, a placeholder token, a far-future
     * expiry, and timestamps. Callers may override location/status/redemption fields.
     *
     * @param id      document id
     * @param claimId id of the approved claim this pass is for
     * @param itemId  id of the found item being returned
     * @param email   claimant email
     * @param status  pass status (active, redeemed, etc.)
     * @param code    one-time PIN code presented at pickup
     * @return a populated ReturnPass
     */
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

    /**
     * Factory that builds a {@link CampusZone} with a label, generic description,
     * and timestamps.
     *
     * @param id    document id
     * @param label human-readable zone label
     * @return a populated CampusZone
     */
    private CampusZone zone(String id, String label) {
        CampusZone zone = new CampusZone();
        zone.setId(id);
        zone.setLabel(label);
        zone.setDescription("Seeded PVHS campus recovery zone.");
        zone.setCreatedDate(NOW);
        zone.setUpdatedDate(NOW);
        return zone;
    }

    /**
     * Factory that builds an {@link AssetRegistryRecord} for school-owned property,
     * with an asset tag, type, return destination department, "active" status, and
     * timestamps.
     *
     * @param id          document id
     * @param tag         asset tag string
     * @param type        asset type (e.g. Chromebook, Camera)
     * @param destination department the asset should be returned to
     * @return a populated AssetRegistryRecord
     */
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

    /**
     * Factory that builds a {@link RecoveryNode} (relay partner) of type
     * "demo_partner" with "active" status and timestamps.
     *
     * @param id   document id
     * @param name node/partner display name
     * @return a populated RecoveryNode
     */
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

    /**
     * Factory that builds a {@link Notification} for a user, with title/message,
     * type, deep-link, related item id, unread state, and timestamps.
     *
     * @param id      document id
     * @param email   recipient user's email
     * @param title   notification title
     * @param message notification body
     * @param type    notification type/category key
     * @param link    in-app link the notification points to
     * @param itemId  related item id (for context/navigation)
     * @return a populated, unread Notification
     */
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

    /**
     * Seeds mock multi-channel delivery records (email, SMS, webhook) for a few
     * seeded notifications, representing how those notifications were dispatched in
     * the demo.
     *
     * @param notificationDeliveries delivery repository; no-op when null
     */
    private void seedNotificationDeliveries(NotificationDeliveryRepository notificationDeliveries) {
        if (notificationDeliveries == null) {
            return;
        }
        // Persist all mock delivery records in one batch.
        notificationDeliveries.saveAll(List.of(
                delivery("ndel_return_pass_email", "notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "email", "return_pass_ready", "mock_sent", "mock_email", "Return Pass ready - Your Return Pass is ready."),
                delivery("ndel_return_pass_sms", "notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "sms", "return_pass_ready", "mock_sent", "mock_sms", "Return Pass ready - Your Return Pass is ready."),
                delivery("ndel_strong_match_email", "notif_001", "jordan.kim@pleasantvalley.edu", "email", "strong_item_match", "mock_sent", "mock_email", "Strong match available - A strong possible match is ready."),
                delivery("ndel_pattern_webhook", "notif_pattern_review_demo", "avery.patel@pleasantvalley.edu", "webhook", "pattern_review_alert", "mock_sent", "mock_webhook", "Pattern Review alert - A loss pattern needs admin review.")
        ));
    }

    /**
     * Factory that builds a {@link NotificationDelivery} record describing how a
     * notification was sent on a given channel. Sets channel-specific recipient
     * fields (email address for email; a masked phone for SMS) and mock provider
     * metadata, marking the record as demo.
     *
     * @param id             document id
     * @param notificationId id of the notification this delivery is for
     * @param email          recipient user's email
     * @param channel        delivery channel (email, sms, webhook)
     * @param eventType      the notification event type
     * @param status         delivery status (e.g. mock_sent)
     * @param provider       mock provider name
     * @param preview        safe message preview text
     * @return a populated, demo-flagged NotificationDelivery
     */
    private NotificationDelivery delivery(String id, String notificationId, String email, String channel, String eventType, String status, String provider, String preview) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(id);
        delivery.setNotificationId(notificationId);
        delivery.setRecipientUserEmail(email);
        // For email deliveries, record the actual recipient email address.
        if ("email".equals(channel)) {
            delivery.setRecipientEmail(email);
        }
        // For SMS deliveries, store a masked phone placeholder (no real number in demo).
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

    /**
     * Factory that builds the single seed {@link AuditLog} entry recording that the
     * demo seed data was created (system action against the "seed" entity).
     *
     * @return a populated AuditLog entry
     */
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

    /**
     * Always upsert the four required demo accounts with correct roles.
     * Uses the seeded ID as the document _id so duplicate-key errors are
     * absorbed by MongoDB's save-or-replace semantics.
     */
    private void upsertDemoUsers(AppUserRepository users) {
        upsertUser(users, "user_002", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin");
        upsertUser(users, "user_staff_demo", "Demo Staff", "staff.demo@pleasantvalley.edu", "staff");
        upsertUser(users, "user_student_demo", "Demo Student", "student.demo@pleasantvalley.edu", "student");
        upsertUser(users, "user_mia_rodriguez", "Mia Rodriguez", "mia.rodriguez@pleasantvalley.edu", "student");
    }

    /** Upsert a single user: preserve existing document when found by email, otherwise insert. */
    private void upsertUser(AppUserRepository users, String id, String fullName, String email, String role) {
        AppUser existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            // Correct the role and name if they drifted (e.g. auto-created by signIn)
            existing.setRole(role);
            existing.setFullName(fullName);
            existing.setUpdatedDate(NOW);
            users.save(existing);
        } else {
            users.save(user(id, fullName, email, role));
        }
    }

    /**
     * Factory that builds an {@link AppUser} with seed notification preferences
     * (email + webhook enabled, SMS off, categories = ["all"]) and timestamps.
     *
     * @param id       document id
     * @param fullName user's full name
     * @param email    user's email (login identifier)
     * @param role     user role (student, staff, admin)
     * @return a populated AppUser
     */
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
