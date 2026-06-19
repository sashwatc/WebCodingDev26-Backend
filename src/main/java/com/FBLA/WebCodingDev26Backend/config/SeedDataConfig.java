package com.FBLA.WebCodingDev26Backend.config;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {
    @Bean
    CommandLineRunner seedData(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            NotificationRepository notifications,
            AuditLogRepository auditLogs,
            AppUserRepository users,
            @Value("${app.seed.enabled}") boolean seedEnabled
    ) {
        return args -> {
            if (!seedEnabled || foundItems.count() > 0) {
                return;
            }

            FoundItem bottle = foundItem(
                    "found_001",
                    "Black Hydro Flask Water Bottle",
                    "food_containers",
                    "Matte black Hydro Flask bottle with a top carry handle and screw cap.",
                    "Black",
                    "Hydro Flask",
                    "Gymnasium",
                    "2026-03-11",
                    "12:15",
                    "approved",
                    "FB-2026-HF82"
            );
            bottle.setPhotoUrls(List.of("/items/black-hydro-flask.jpg"));
            bottle.setTags(List.of("water bottle", "hydro flask", "black", "gym"));
            bottle.setStorageLocation("Main Office shelf B2");
            bottle.setFinderName("Coach Miller");
            bottle.setFinderEmail("coach.miller@pleasantvalley.edu");
            bottle.setFinderRole("staff");
            bottle.setPriority("medium");
            bottle.setCondition("good");
            bottle.setAiDescription("A matte black Hydro Flask water bottle with a top handle and white logo.");
            bottle.setDistinguishingFeatures("Large black bottle with white Hydro Flask logo and top handle");

            FoundItem backpack = foundItem(
                    "found_002",
                    "Blue JanSport Backpack",
                    "bags_cases",
                    "Royal blue JanSport backpack with math notebook and tennis keychain.",
                    "Blue",
                    "JanSport",
                    "Student Lounge",
                    "2026-03-09",
                    "15:05",
                    "claimed",
                    "FB-2026-JS27"
            );
            backpack.setPhotoUrls(List.of("/images/blue-backpack.png"));
            backpack.setTags(List.of("backpack", "jansport", "blue", "student lounge"));
            backpack.setStorageLocation("Counselor office storage closet");
            backpack.setFinderName("Jamie Lopez");
            backpack.setFinderEmail("jamie.lopez@pleasantvalley.edu");
            backpack.setFinderRole("student");
            backpack.setPriority("medium");
            backpack.setCondition("fair");

            foundItems.saveAll(List.of(bottle, backpack));

            LostReport report = new LostReport();
            report.setId("lost_001");
            report.setTitle("Missing blue backpack");
            report.setCategory("bags_cases");
            report.setDescription("Blue JanSport backpack with a tennis keychain.");
            report.setColor("Blue");
            report.setBrand("JanSport");
            report.setLocationLost("Student Lounge");
            report.setDateLost("2026-03-09");
            report.setContactName("Jordan Kim");
            report.setContactEmail("jordan.kim@pleasantvalley.edu");
            report.setStatus("open");
            report.setUrgency("medium");
            report.setMatchedItems(List.of("found_002"));
            report.setCreatedDate("2026-03-09T16:00:00Z");
            report.setUpdatedDate("2026-03-09T16:00:00Z");
            lostReports.save(report);

            Claim claim = new Claim();
            claim.setId("claim_001");
            claim.setFoundItemId("found_002");
            claim.setClaimantName("Jordan Kim");
            claim.setClaimantEmail("jordan.kim@pleasantvalley.edu");
            claim.setClaimReason("This matches my backpack and the tennis keychain inside.");
            claim.setIdentifyingDetails("Green tennis racket keychain in the front pocket.");
            claim.setStatus("approved");
            claim.setRiskScore(12);
            claim.setRiskFlags(List.of("sufficient detail provided"));
            claim.setCreatedDate("2026-03-10T10:00:00Z");
            claim.setUpdatedDate("2026-03-10T11:00:00Z");
            claims.save(claim);

            Notification notification = new Notification();
            notification.setId("notif_001");
            notification.setUserEmail("jordan.kim@pleasantvalley.edu");
            notification.setTitle("Potential match found");
            notification.setMessage("We found a possible match for your lost item report.");
            notification.setType("match_found");
            notification.setLink("/UserDashboard");
            notification.setRelatedItemId("found_002");
            notification.setIsRead(false);
            notification.setCreatedDate("2026-03-10T10:15:00Z");
            notification.setUpdatedDate("2026-03-10T10:15:00Z");
            notifications.save(notification);

            AuditLog auditLog = new AuditLog();
            auditLog.setId("audit_001");
            auditLog.setAction("Seed data created");
            auditLog.setEntityType("system");
            auditLog.setEntityId("seed");
            auditLog.setPerformedBy("system");
            auditLog.setDetails("Initial Lost Then Found demo records loaded.");
            auditLog.setCreatedDate("2026-03-10T10:20:00Z");
            auditLogs.save(auditLog);

            users.save(user("user_001", "Jordan Kim", "jordan.kim@pleasantvalley.edu", "student"));
            users.save(user("user_002", "Avery Patel", "avery.patel@pleasantvalley.edu", "admin"));
        };
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
        item.setCreatedDate("2026-03-10T10:00:00Z");
        item.setUpdatedDate("2026-03-10T10:00:00Z");
        return item;
    }

    private AppUser user(String id, String fullName, String email, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setAvatarUrl("");
        user.setCreatedDate("2026-03-10T10:00:00Z");
        user.setUpdatedDate("2026-03-10T10:00:00Z");
        return user;
    }
}
