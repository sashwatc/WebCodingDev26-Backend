package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.service.AdminWorkflowService;
import com.FBLA.WebCodingDev26Backend.service.ClockService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.MatchmakingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminWorkflowService workflow;
    private final DemoAuthorizationService authorizationService;
    private final AppUserRepository userRepository;
    private final FoundItemRepository foundItemRepository;
    private final ClockService clock;
    private final MatchmakingService matchmakingService;

    public AdminDashboardController(AdminWorkflowService workflow, DemoAuthorizationService authorizationService, AppUserRepository userRepository, FoundItemRepository foundItemRepository, ClockService clock, MatchmakingService matchmakingService) {
        this.workflow = workflow;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.foundItemRepository = foundItemRepository;
        this.clock = clock;
        this.matchmakingService = matchmakingService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.dashboard();
    }

    /**
     * The Recovery Center summary is an optional aggregation; the demo backend does
     * not maintain it, so return an empty object (instead of 404). The dashboard
     * treats an absent summary by deriving counts from claims and lost reports.
     */
    @GetMapping("/recovery-center")
    public Map<String, Object> recoveryCenter(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return Map.of();
    }

    @GetMapping("/lost-reports")
    public List<?> lostReports(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listLostReports();
    }

    @GetMapping("/claims")
    public List<Claim> claims(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listClaims();
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listAuditLogs();
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listNotifications();
    }

    @PostMapping("/claims/{id}/approve")
    public Claim approveClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.approveClaim(id, admin, data);
    }

    @PostMapping("/claims/{id}/complete")
    public Claim completeClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.completeClaim(id, admin, data);
    }

    @PostMapping("/claims/{id}/deny")
    public Claim denyClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.denyClaim(id, admin, data);
    }

    @PostMapping("/claims/{id}/request-more-info")
    public Claim requestMoreInfo(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.requestMoreInfo(id, admin, data);
    }

    @PostMapping("/items/{id}/archive")
    public FoundItem archiveItem(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.archiveItem(id, admin, data);
    }

    @GetMapping("/users")
    public List<AppUser> users(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return userRepository.findAll();
    }

    @PatchMapping("/users/{id}/role")
    public AppUser updateUserRole(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        String role = body.get("role") != null ? String.valueOf(body.get("role")).trim().toLowerCase() : "";
        if (!List.of("student", "staff", "admin", "suspended").contains(role)) {
            throw new BadRequestException("Invalid role.");
        }
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
        user.setRole(role);
        user.setUpdatedDate(clock.now());
        return userRepository.save(user);
    }

    @PatchMapping("/lost-reports/{reportId}/matches/{foundItemId}")
    public LostReport decideLostReportMatch(
            @PathVariable String reportId,
            @PathVariable String foundItemId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        String decision = body.get("decision") != null ? String.valueOf(body.get("decision")).trim().toLowerCase() : "";
        if (!List.of("confirmed", "rejected", "linked").contains(decision)) {
            throw new BadRequestException("Decision must be confirmed, rejected, or linked.");
        }
        return matchmakingService.decideMatch(reportId, foundItemId, decision);
    }

    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.listAuditLogs();
    }

    @GetMapping("/patterns")
    public Map<String, Object> patterns(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        List<FoundItem> items = foundItemRepository.findAll();
        Map<String, Long> byCategory = items.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory() == null ? "unknown" : item.getCategory(), Collectors.counting()));
        Map<String, Long> byStatus = items.stream()
                .collect(Collectors.groupingBy(item -> item.getStatus() == null ? "unknown" : item.getStatus(), Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_items", items.size());
        result.put("by_category", byCategory);
        result.put("by_status", byStatus);
        return result;
    }
}
