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

/**
 * REST controller that assembles the signed-in student's personal dashboard.
 *
 * <p>Base route: {@code /api/dashboard}. Returns JSON. Pulls together the student's active and
 * recent claims, their open lost reports, their watched ("saved") items hydrated with public item
 * details, and their recent/unread notifications into a single response.
 *
 * <p>Requires a signed-in user, identified via the demo {@code X-Demo-User-Email} header; all data
 * is scoped to that user's email.
 *
 * <p>Collaborators: {@link ClaimRepository}, {@link LostReportRepository},
 * {@link NotificationRepository}, {@link WatchedItemRepository}, {@link FoundItemRepository}, and
 * {@link DemoAuthorizationService}.
 */
@RestController // JSON REST controller
@RequestMapping("/api/dashboard") // shared base path for all handlers
public class StudentDashboardController {
    /** Claim statuses that are "done" — excluded from active claims, used to pick recent ones. */
    private static final Set<String> TERMINAL_STATUSES = Set.of("completed", "rejected", "cancelled");
    /** Active-claim statuses meaning an item is ready for the student to pick up. */
    private static final Set<String> PICKUP_READY_STATUSES = Set.of("approved", "pass_ready");

    /** Source of the student's claims (active, recent, and pickup-ready counts). */
    private final ClaimRepository claims;
    /** Source of the student's lost reports. */
    private final LostReportRepository lostReports;
    /** Source of the student's notifications (recent list + unread count). */
    private final NotificationRepository notifications;
    /** Source of the student's watched/saved items. */
    private final WatchedItemRepository watchedItems;
    /** Used to hydrate each watched item with its public found-item details. */
    private final FoundItemRepository foundItems;
    /** Resolves the calling user from the {@code X-Demo-User-Email} header. */
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the repositories and authorization collaborators. */
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

    /**
     * GET {@code /api/dashboard/student} — assemble the current student's dashboard.
     *
     * <p>Response map fields:
     * <ul>
     *   <li>{@code activeClaims}: claims not in a {@link #TERMINAL_STATUSES} status.</li>
     *   <li>{@code recentClaims}: up to 3 terminal (completed/rejected/cancelled) claims, most
     *       recently updated first.</li>
     *   <li>{@code activeLostReports}: the user's lost reports (by contact email).</li>
     *   <li>{@code savedItems}: each watched item paired with its public found-item details (when the
     *       referenced item still exists).</li>
     *   <li>{@code recentNotifications}: the 5 newest notifications.</li>
     *   <li>{@code unreadNotificationCount}: count of notifications not marked read.</li>
     *   <li>{@code pickupReadyCount}: active claims in a {@link #PICKUP_READY_STATUSES} status.</li>
     * </ul>
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the dashboard map described above.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
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
        // Active = anything not in a terminal status.
        List<Claim> activeClaims = allClaims.stream()
                .filter(c -> !TERMINAL_STATUSES.contains(normalize(c.getStatus())))
                .toList();
        // Recent = the 3 most-recently-updated terminal claims (null dates sort last).
        List<Claim> recentClaims = allClaims.stream()
                .filter(c -> TERMINAL_STATUSES.contains(normalize(c.getStatus())))
                .sorted(Comparator.comparing(c -> c.getUpdatedDate() == null ? "" : c.getUpdatedDate(), Comparator.reverseOrder()))
                .limit(3)
                .toList();

        List<LostReport> activeLostReports = lostReports.findByContactEmail(email);

        // For each watched item, attach the public view of the referenced found item if it still exists.
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
        List<Notification> recentNotifications = allNotifications.stream().limit(5).toList(); // newest 5
        // Count notifications whose read flag is not explicitly true (null/false both count as unread).
        long unreadCount = allNotifications.stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .count();

        // How many active claims are ready for pickup (drives a dashboard badge).
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

    /** Null-safe status normalization (trim + lower-case) for set-membership comparisons. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
