package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.WatchedItemRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class StudentDashboardController {
    private static final Set<String> TERMINAL_STATUSES = Set.of("completed", "rejected", "cancelled");
    private static final Set<String> PICKUP_READY_STATUSES = Set.of("approved", "pass_ready");

    private final ClaimRepository claims;
    private final LostReportRepository lostReports;
    private final NotificationRepository notifications;
    private final WatchedItemRepository watchedItems;
    private final FoundItemRepository foundItems;
    private final DemoAuthorizationService authorizationService;

    public StudentDashboardController(
            ClaimRepository claims,
            LostReportRepository lostReports,
            NotificationRepository notifications,
            WatchedItemRepository watchedItems,
            FoundItemRepository foundItems,
            DemoAuthorizationService authorizationService) {
        this.claims = claims;
        this.lostReports = lostReports;
        this.notifications = notifications;
        this.watchedItems = watchedItems;
        this.foundItems = foundItems;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/student")
    public Map<String, Object> studentDashboard(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        String email = user.getEmail();

        List<Claim> allClaims = claims.findByClaimantEmail(email);
        List<Claim> activeClaims = allClaims.stream()
                .filter(c -> !TERMINAL_STATUSES.contains(normalize(c.getStatus())))
                .toList();
        List<Claim> recentClaims = allClaims.stream()
                .filter(c -> TERMINAL_STATUSES.contains(normalize(c.getStatus())))
                .sorted(Comparator.comparing(c -> c.getUpdatedDate() == null ? "" : c.getUpdatedDate(), Comparator.reverseOrder()))
                .limit(3)
                .toList();

        List<LostReport> activeLostReports = lostReports.findByContactEmail(email);

        List<Map<String, Object>> savedItemsData = watchedItems.findByUserId(email).stream()
                .map(wi -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("watchedItem", wi);
                    foundItems.findById(wi.getFoundItemId()).ifPresent(item ->
                            entry.put("item", PublicFoundItemResponse.from(item)));
                    return entry;
                })
                .toList();

        List<Notification> allNotifications = notifications.findByUserEmailOrderByCreatedDateDesc(email);
        List<Notification> recentNotifications = allNotifications.stream().limit(5).toList();
        long unreadCount = allNotifications.stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .count();

        long pickupReadyCount = activeClaims.stream()
                .filter(c -> PICKUP_READY_STATUSES.contains(normalize(c.getStatus())))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeClaims", activeClaims);
        result.put("recentClaims", recentClaims);
        result.put("activeLostReports", activeLostReports);
        result.put("savedItems", savedItemsData);
        result.put("recentNotifications", recentNotifications);
        result.put("unreadNotificationCount", unreadCount);
        result.put("pickupReadyCount", pickupReadyCount);
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
