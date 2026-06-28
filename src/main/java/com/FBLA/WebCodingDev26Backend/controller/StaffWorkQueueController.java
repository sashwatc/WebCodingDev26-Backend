package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.SupportTicket;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.SupportTicketRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that powers the staff work-queue / triage dashboard.
 *
 * <p>Base route: {@code /api/staff}. Returns JSON. Aggregates the items needing staff attention
 * (newly found items awaiting verification, pending claims, claims that need more info, and open
 * support tickets) and provides a paginated claim browser.
 *
 * <p>Every endpoint requires a staff or admin caller, identified via the demo
 * {@code X-Demo-User-Email} header. Data is read directly from repositories and filtered/sorted
 * in-memory rather than via dedicated queries.
 *
 * <p>Collaborators: {@link FoundItemRepository}, {@link ClaimRepository},
 * {@link SupportTicketRepository}, and {@link DemoAuthorizationService}.
 */
@RestController // JSON REST controller
@RequestMapping("/api/staff") // shared base path for all handlers
public class StaffWorkQueueController {
    /** Claim statuses considered "pending" (awaiting staff review) for the queue. */
    private static final Set<String> PENDING_CLAIM_STATUSES = Set.of("submitted", "under_review", "pending_review", "pending");
    /** Claim statuses meaning the claim is blocked awaiting more info from the claimant. */
    private static final Set<String> NEEDS_INFO_STATUSES = Set.of("need_more_info", "needs_info", "more_info_requested");

    /** Source of found-item intake records (the "pending items" bucket). */
    private final FoundItemRepository foundItems;
    /** Source of claim records (pending / needs-info buckets and the claim browser). */
    private final ClaimRepository claims;
    /** Source of support tickets (the open-tickets bucket). */
    private final SupportTicketRepository supportTickets;
    /** Resolves the caller from the demo header and enforces staff/admin access. */
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the repositories and authorization collaborators. */
    public StaffWorkQueueController(
            FoundItemRepository foundItems,
            ClaimRepository claims,
            SupportTicketRepository supportTickets,
            DemoAuthorizationService authorizationService) {
        this.foundItems = foundItems;
        this.claims = claims;
        this.supportTickets = supportTickets;
        this.authorizationService = authorizationService;
    }

    /**
     * GET {@code /api/staff/queue} — the full triage dashboard payload.
     *
     * <p>Builds four buckets, each shaped as {@code {items:[...], count:N}}:
     * <ul>
     *   <li>{@code pendingItems}: found items still in intake status {@code found} (not yet
     *       verified), newest first.</li>
     *   <li>{@code pendingClaims}: claims in a {@link #PENDING_CLAIM_STATUSES} status, newest first.</li>
     *   <li>{@code needsInfo}: claims in a {@link #NEEDS_INFO_STATUSES} status, most recently updated
     *       first.</li>
     *   <li>{@code supportTickets}: support tickets with status {@code open}.</li>
     * </ul>
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with an ordered map containing the four buckets above.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/queue")
    public Map<String, Object> queue(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);

        // Pending items: FoundItems with status FOUND (intake, not yet verified)
        // Accept either case for the status string; sort by createdDate descending (newest first),
        // treating a null date as "" so it sorts last.
        List<FoundItem> pendingItemsList = foundItems.findAll().stream()
                .filter(item -> "found".equalsIgnoreCase(item.getStatus()) || "FOUND".equals(item.getStatus()))
                .sorted(Comparator.comparing(i -> i.getCreatedDate() == null ? "" : i.getCreatedDate(), Comparator.reverseOrder()))
                .toList();

        // Pending claims: Claims with status submitted or under_review
        List<Claim> allClaims = claims.findAll();
        List<Claim> pendingClaimsList = allClaims.stream()
                .filter(c -> PENDING_CLAIM_STATUSES.contains(normalize(c.getStatus())))
                .sorted(Comparator.comparing(c -> c.getCreatedDate() == null ? "" : c.getCreatedDate(), Comparator.reverseOrder()))
                .toList();

        // Needs info: Claims with status need_more_info
        List<Claim> needsInfoList = allClaims.stream()
                .filter(c -> NEEDS_INFO_STATUSES.contains(normalize(c.getStatus())))
                .sorted(Comparator.comparing(c -> c.getUpdatedDate() == null ? "" : c.getUpdatedDate(), Comparator.reverseOrder()))
                .toList();

        Map<String, Object> pendingItemsMap = new LinkedHashMap<>();
        pendingItemsMap.put("items", pendingItemsList);
        pendingItemsMap.put("count", pendingItemsList.size());

        Map<String, Object> pendingClaimsMap = new LinkedHashMap<>();
        pendingClaimsMap.put("items", pendingClaimsList);
        pendingClaimsMap.put("count", pendingClaimsList.size());

        Map<String, Object> needsInfoMap = new LinkedHashMap<>();
        needsInfoMap.put("items", needsInfoList);
        needsInfoMap.put("count", needsInfoList.size());

        // Open support tickets
        List<SupportTicket> openTickets = supportTickets.findByStatus("open");
        Map<String, Object> supportTicketsMap = new LinkedHashMap<>();
        supportTicketsMap.put("items", openTickets);
        supportTicketsMap.put("count", openTickets.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingItems", pendingItemsMap);
        result.put("pendingClaims", pendingClaimsMap);
        result.put("needsInfo", needsInfoMap);
        result.put("supportTickets", supportTicketsMap);
        return result;
    }

    /**
     * GET {@code /api/staff/queue/claims} — paginated, optionally status-filtered claim browser.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @param status optional case-insensitive exact-match status filter; absent/blank returns all.
     * @param page zero-based page index (default 0).
     * @param size page size (default 20).
     * @return 200 OK with {@code {items, total, page, size}} where {@code items} is the requested page
     *         of claims sorted newest first and {@code total} is the full filtered count. Out-of-range
     *         pages yield an empty {@code items} list (indices are clamped).
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/queue/claims")
    public Map<String, Object> queueClaims(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);

        List<Claim> allClaims = claims.findAll();
        List<Claim> filtered = allClaims.stream()
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(c.getStatus()))
                .sorted(Comparator.comparing(c -> c.getCreatedDate() == null ? "" : c.getCreatedDate(), Comparator.reverseOrder()))
                .toList();

        int total = filtered.size();
        // Clamp the slice bounds to [0, total] so an out-of-range page returns an empty sublist
        // instead of throwing IndexOutOfBounds.
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Claim> pageItems = filtered.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", pageItems);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** Null-safe status normalization (trim + lower-case) for set-membership comparisons. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
