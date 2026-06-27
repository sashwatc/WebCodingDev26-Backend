package com.FBLA.WebCodingDev26Backend.config;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
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

                if (foundItems.count() > 0) {
                    seedRealisticActivity(foundItems, lostReports, claims, notifications);
                    return;
                }

                seedZones(campusZones);
                seedAssets(assetRecords);
                seedItems(foundItems);
                seedLostReports(lostReports);
                seedClaims(claims);
                seedRecoveryCases(recoveryCases);
                seedPasses(returnPasses);
                seedRelay(recoveryNodes);
                seedCustody(custodyLedgerService);
                seedRealisticActivity(foundItems, lostReports, claims, notifications);

                notifications.save(notification("notif_return_pass_demo", "riley.chen@pleasantvalley.edu", "Return Pass ready", "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions.", "return_pass_ready", "/return-pass/pass_calculator_active", "found_claimed_calculator"));
                notifications.save(notification("notif_pattern_review_demo", "avery.patel@pleasantvalley.edu", "Pattern Review alert", "A loss pattern needs admin review.", "pattern_review_alert", "/admin/pattern-review", "alert_demo_pattern"));
                seedNotificationDeliveries(notificationDeliveries);
                auditLogs.save(auditLog());
                users.save(user("user_001", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "student"));
                users.save(user("user_003", "Riley Chen", "riley.chen@pleasantvalley.edu", "student"));
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

    private void seedItems(FoundItemRepository foundItems) {
        FoundItem bottle = foundItem("found_001", "Black Hydro Flask Water Bottle", "food_containers", "Matte black Hydro Flask bottle with a top carry handle and screw cap.", "Black", "Hydro Flask", "Gymnasium", "2026-03-11", "12:15", ItemStatus.FOUND, "FB-2026-HF82");
        bottle.setPhotoUrls(List.of("/items/black-hydro-flask.jpg"));
        bottle.setTags(List.of("water bottle", "hydro flask", "black", "gym"));
        bottle.setStorageLocation("Main Office shelf B2");
        bottle.setFinderName("Coach Miller");
        bottle.setFinderEmail("coach.miller@pleasantvalley.edu");
        bottle.setFinderRole("staff");

        // found_002 (Blue JanSport Backpack) lives in seedRealisticActivity instead,
        // so it is also added to databases that were already seeded by an older build.

        FoundItem airpods = foundItem("found_airpods_game", "Black AirPods-style Case", "electronics", "Black wireless earbud case found after the basketball game.", "Black", "Apple", "Gym Bleachers", "2026-03-14", "20:40", ItemStatus.FOUND, "FB-2026-AP14");
        airpods.setEventHubId("hub_basketball_game");
        airpods.setCampusZoneId("zone_gym_bleachers");
        airpods.setPhotoUrls(List.of("/items/airpods-pro-case.png"));
        airpods.setTags(List.of("airpods", "black", "earbuds", "gym bleachers"));
        airpods.setPrivateVerificationClues(List.of("small silver initials on the hinge", "tiny scratch along the left back corner"));
        airpods.setStorageLocation("Main Office sealed bin A1");

        FoundItem calculator = foundItem("found_calculator_demo", "Silver Graphing Calculator", "electronics", "Silver graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.FOUND, "FB-2026-CAL55");
        calculator.setEventHubId("hub_basketball_game");
        calculator.setCampusZoneId("zone_gym_entrance");
        calculator.setPhotoUrls(List.of("/items/ti-calculator.png"));
        calculator.setPrivateVerificationClues(List.of("name label under the slide cover", "faint star sticker on the back"));
        calculator.setTags(List.of("calculator", "silver", "texas instruments", "gym entrance"));
        calculator.setStorageLocation("Main Office sealed bin A2");

        FoundItem passItem = foundItem("found_claimed_calculator", "Silver Graphing Calculator", "electronics", "TI-style graphing calculator found near the gym entrance.", "Silver", "Texas Instruments", "Gym Entrance", "2026-03-14", "19:30", ItemStatus.VERIFIED, "FB-2026-CAL77");
        passItem.setEventHubId("hub_basketball_game");
        passItem.setCampusZoneId("zone_gym_entrance");
        passItem.setPrivateVerificationClues(List.of("name label under the slide cover"));
        passItem.setPhotoUrls(List.of("/items/ti-calculator.png"));
        passItem.setStorageLocation("Main Office pickup drawer");

        FoundItem returned = foundItem("found_returned_lanyard", "PVHS Lanyard With Keys", "personal_items", "Blue PVHS lanyard with two keys.", "Blue", "PVHS", "Athletics Office", "2026-03-13", "17:20", ItemStatus.ARCHIVED, "FB-2026-KEY22");
        returned.setEventHubId("hub_basketball_game");
        returned.setCampusZoneId("zone_athletics");
        returned.setPhotoUrls(List.of("/items/pvhs-lanyard.png"));
        returned.setClaimConfirmed(true);
        returned.setClaimConfirmedAt("2026-03-14T16:30:00Z");

        FoundItem chromebook = foundItem("found_asset_chromebook", "PVHS Chromebook", "electronics", "School-owned Chromebook with asset tag.", "Gray", "Lenovo", "Library Study Area", "2026-03-12", "11:05", ItemStatus.FOUND, "FB-2026-CB1042");
        chromebook.setAssetTag("PVHS-CB-1042");
        chromebook.setAssetRecordId("asset_cb_1042");
        chromebook.setDepartmentDestination("Technology Office");
        chromebook.setRestrictedVisibility(true);
        chromebook.setPhotoUrls(List.of("/images/locker-cool.png"));
        chromebook.setStorageLocation("Technology Office intake shelf");

        FoundItem hoodie = foundItem("found_blue_hoodie", "Blue Zip-Up Hoodie", "clothing", "Blue zip-up hoodie with a school logo on the left chest.", "Blue", "", "Cafeteria", "2026-03-15", "12:30", ItemStatus.FOUND, "FB-2026-HDY01");
        hoodie.setCampusZoneId("zone_cafeteria");
        hoodie.setPhotoUrls(List.of("/items/nike-hoodie.png"));
        hoodie.setTags(List.of("hoodie", "blue", "zip-up", "clothing"));
        hoodie.setStorageLocation("Main Office clothing bin");

        FoundItem umbrella = foundItem("found_red_umbrella", "Red Compact Umbrella", "personal_items", "Small red compact umbrella found in the library study area.", "Red", "", "Library Study Area", "2026-03-15", "14:00", ItemStatus.FOUND, "FB-2026-UMB02");
        umbrella.setCampusZoneId("zone_library");
        umbrella.setPhotoUrls(List.of("/items/red-umbrella.png"));
        umbrella.setTags(List.of("umbrella", "red", "compact", "library"));
        umbrella.setStorageLocation("Main Office shelf B3");

        foundItems.saveAll(List.of(bottle, airpods, calculator, passItem, returned, chromebook, hoodie, umbrella));
    }

    private void seedRealisticActivity(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications
    ) {
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

        LostReport rainJacketReport = lostReport("lost_black_rain_jacket", "Missing black rain jacket", "clothing", "Black lightweight rain jacket last seen by the bus loop.", "Black", "Columbia", "Bus Loop", "2026-03-15", "olivia.brown@pleasantvalley.edu");
        rainJacketReport.setContactName("Olivia Brown");
        rainJacketReport.setCampusZoneId("zone_bus_loop");
        rainJacketReport.setTimeLost("15:25");
        rainJacketReport.setExtraNotes("No matching found item yet; student checked advisory and locker.");
        saveSeeded(lostReports, rainJacketReport);

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

        saveIfMissing(notifications, notification("notif_charger_match", "sophia.nguyen@pleasantvalley.edu", "Possible charger match", "A white USB-C charger may match your lost report.", "strong_item_match", "/UserDashboard", "found_usbc_charger"));
        saveIfMissing(notifications, notification("notif_textbook_info", "jordan.kim@pleasantvalley.edu", "More info needed", "Staff requested one more detail for your AP Biology textbook claim.", "claim_more_info", "/claims/claim_textbook_needs_info", "found_ap_biology_textbook"));
        saveIfMissing(notifications, notification("notif_sunglasses_reviewed", "mia.rodriguez@pleasantvalley.edu", "Claim reviewed", "Staff reviewed the sunglasses claim and left a decision.", "claim_reviewed", "/claims/claim_sunglasses_rejected", "found_rayban_sunglasses"));
        saveIfMissing(notifications, notification("notif_lunch_review", "sophia.nguyen@pleasantvalley.edu", "Claim under review", "Staff are reviewing your lunch bag claim against private intake details.", "claim_review", "/claim?item=found_lunch_bag_floral", "found_lunch_bag_floral"));
        saveIfMissing(notifications, notification("notif_debate_more_info", "emma.wilson@pleasantvalley.edu", "More info needed", "Staff need one more private detail before approving the debate folder claim.", "claim_more_info_requested", "/claim?item=found_debate_folder", "found_debate_folder"));
        saveIfMissing(notifications, notification("notif_clarinet_match", "noah.anderson@pleasantvalley.edu", "Possible match found", "A clarinet mouthpiece case may match your lost report.", "strong_item_match", "/browse", "found_clarinet_mouthpiece"));
        saveIfMissing(notifications, notification("notif_earring_review", "hannah.lee@pleasantvalley.edu", "Jewelry claim received", "Your earring claim is queued for staff review.", "claim_review", "/claim?item=found_pearl_earring", "found_pearl_earring"));
        saveIfMissing(notifications, notification("notif_001", "jordan.kim@pleasantvalley.edu", "Strong match available", "A strong possible match is ready for review.", "strong_item_match", "/UserDashboard", "found_002"));
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
        // claim_001 (Jordan's approved backpack claim) lives in seedRealisticActivity
        // so it also lands on already-seeded databases.
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

    private void seedRelay(RecoveryNodeRepository recoveryNodes) {
        if (recoveryNodes == null) {
            return;
        }
        recoveryNodes.saveAll(List.of(
                node("node_main_office", "PVHS Main Office"),
                node("node_athletics", "PVHS Athletics"),
                node("node_transportation", "PVHS Transportation"),
                node("node_fine_arts", "PVHS Fine Arts")
        ));
    }

    private void seedCustody(CustodyLedgerService custodyLedgerService) {
        if (custodyLedgerService == null) {
            return;
        }
        // Custody events are an append-only ledger, so guard against re-seeding to
        // avoid duplicate chains when the full seed runs on a non-empty database.
        if (!custodyLedgerService.list("found_airpods_game").isEmpty()) {
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

    private void saveIfMissing(NotificationRepository repository, Notification notification) {
        if (repository != null && repository.findById(notification.getId()).isEmpty()) {
            repository.save(notification);
        }
    }

    private void saveSeeded(FoundItemRepository repository, FoundItem item) {
        if (repository == null) {
            return;
        }
        FoundItem existing = repository.findById(item.getId()).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(item);
        }
    }

    private void saveSeeded(LostReportRepository repository, LostReport report) {
        if (repository == null) {
            return;
        }
        LostReport existing = repository.findById(report.getId()).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(report);
        }
    }

    private void saveSeeded(ClaimRepository repository, Claim claim) {
        if (repository == null) {
            return;
        }
        Claim existing = repository.findById(claim.getId()).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDemo())) {
            repository.save(claim);
        }
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
