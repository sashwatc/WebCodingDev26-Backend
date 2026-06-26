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

@RestController
@RequestMapping("/api/staff")
public class StaffWorkQueueController {
    private static final Set<String> PENDING_CLAIM_STATUSES = Set.of("submitted", "under_review", "pending_review", "pending");
    private static final Set<String> NEEDS_INFO_STATUSES = Set.of("need_more_info", "needs_info", "more_info_requested");

    private final FoundItemRepository foundItems;
    private final ClaimRepository claims;
    private final SupportTicketRepository supportTickets;
    private final DemoAuthorizationService authorizationService;

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

    @GetMapping("/queue")
    public Map<String, Object> queue(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);

        // Pending items: FoundItems with status FOUND (intake, not yet verified)
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
