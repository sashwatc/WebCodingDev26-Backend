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

/**
 * Staff/admin back-office controller for the lost-and-found workflow.
 *
 * <p>Serves the admin dashboard, moderation queues, and claim-lifecycle actions
 * under the base route {@code /api/admin}. Most endpoints require a staff OR admin
 * caller (identified by the {@code X-Demo-User-Email} request header and verified
 * through {@link DemoAuthorizationService}); a few sensitive operations (user list,
 * role changes, audit log, analytics) require a full admin.</p>
 *
 * <p>Collaborators:
 * <ul>
 *   <li>{@link AdminWorkflowService} — the bulk of the business logic: dashboard
 *       aggregation, listing queues, and the claim approve/deny/complete/archive
 *       state transitions.</li>
 *   <li>{@link DemoAuthorizationService} — resolves the caller's identity/role from
 *       the demo header and enforces staff/admin authorization.</li>
 *   <li>{@link AppUserRepository} — direct user lookups for the admin user list and
 *       role-change endpoint.</li>
 *   <li>{@link FoundItemRepository} — direct item reads for the analytics/patterns
 *       endpoint.</li>
 *   <li>{@link ClockService} — supplies the current timestamp for audit/update fields.</li>
 *   <li>{@link MatchmakingService} — applies confirm/reject/link decisions on
 *       lost-report ↔ found-item match suggestions.</li>
 * </ul></p>
 */
@RestController // marks this as a REST controller: every handler's return value is serialized (JSON) into the response body
@RequestMapping("/api/admin") // common URL prefix for every endpoint in this class
public class AdminDashboardController {
    // Core admin workflow service: dashboard data, queue listings, and claim/item state transitions.
    private final AdminWorkflowService workflow;
    // Resolves the caller from the demo header and enforces staff/admin authorization.
    private final DemoAuthorizationService authorizationService;
    // Direct access to user records (admin user list and role updates).
    private final AppUserRepository userRepository;
    // Direct access to found items (used to compute the analytics/patterns summary).
    private final FoundItemRepository foundItemRepository;
    // Abstraction over "current time" so timestamps are testable/controllable.
    private final ClockService clock;
    // Applies staff decisions on suggested lost-report/found-item matches.
    private final MatchmakingService matchmakingService;

    /**
     * Constructor injection of all collaborators. Spring supplies each bean
     * automatically when the controller is created.
     */
    public AdminDashboardController(AdminWorkflowService workflow, DemoAuthorizationService authorizationService, AppUserRepository userRepository, FoundItemRepository foundItemRepository, ClockService clock, MatchmakingService matchmakingService) {
        this.workflow = workflow;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.foundItemRepository = foundItemRepository;
        this.clock = clock;
        this.matchmakingService = matchmakingService;
    }

    /**
     * GET /api/admin/dashboard — top-level admin dashboard summary.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header (optional on the
     *                  wire, but authorization will reject the request if it does not resolve to a staff/admin)
     * @return a map of aggregated dashboard metrics built by {@link AdminWorkflowService#dashboard()}
     * Status: 200 OK on success. Throws (typically 401/403 via the authorization service) if the
     * caller is not a staff member or admin.
     */
    @GetMapping("/dashboard") // HTTP GET on /api/admin/dashboard
    public Map<String, Object> dashboard(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: only staff or admins may view the dashboard.
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.dashboard();
    }

    /**
     * The Recovery Center summary is an optional aggregation; the demo backend does
     * not maintain it, so return an empty object (instead of 404). The dashboard
     * treats an absent summary by deriving counts from claims and lost reports.
     */
    /**
     * GET /api/admin/recovery-center — Recovery Center summary (optional aggregation).
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return an empty map (see the note above for why) with status 200 OK
     * Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping("/recovery-center")
    public Map<String, Object> recoveryCenter(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        return Map.of();
    }

    /**
     * GET /api/admin/lost-reports — list every lost report for the moderation queue.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return the full list of lost reports from {@link AdminWorkflowService#listLostReports()}; 200 OK
     * Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping("/lost-reports")
    public List<?> lostReports(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listLostReports();
    }

    /**
     * GET /api/admin/claims — list every claim for the staff review queue.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return the full list of {@link Claim} records; 200 OK
     * Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping("/claims")
    public List<Claim> claims(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listClaims();
    }

    /**
     * GET /api/admin/audit-logs — list audit-log entries (staff-visible variant).
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return the list of {@link AuditLog} records; 200 OK
     * Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listAuditLogs();
    }

    /**
     * GET /api/admin/notifications — list notifications surfaced to staff/admins.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return the list of {@link Notification} records; 200 OK
     * Throws via the authorization service if the caller is not staff/admin.
     */
    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.listNotifications();
    }

    /**
     * POST /api/admin/claims/{id}/approve — approve a pending claim.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @param data      optional request body: extra action metadata (e.g. notes/pickup details) passed
     *                  through to the workflow service; may be null
     * @return the updated {@link Claim} (now approved); 200 OK
     * Authorization: staff/admin required (the resolved {@link AppUser} is recorded as the actor).
     * Errors: throws if the caller is unauthorized, or (from the workflow) if the claim does not exist
     * or is not in an approvable state.
     */
    @PostMapping("/claims/{id}/approve")
    public Claim approveClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        // Authorize and capture the acting staff/admin so the workflow can attribute the action.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.approveClaim(id, admin, data);
    }

    /**
     * POST /api/admin/claims/{id}/complete — mark an approved claim as completed (item handed over).
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @param data      optional request body: completion metadata passed through to the workflow; may be null
     * @return the updated {@link Claim} (now completed); 200 OK
     * Authorization: staff/admin required. Errors: throws if unauthorized, or (from the workflow) if the
     * claim is missing or not in a completable state.
     */
    @PostMapping("/claims/{id}/complete")
    public Claim completeClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        // Authorize and capture the acting staff/admin.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.completeClaim(id, admin, data);
    }

    /**
     * POST /api/admin/claims/{id}/deny — deny/reject a claim.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @param data      optional request body: denial reason/notes passed through to the workflow; may be null
     * @return the updated {@link Claim} (now denied); 200 OK
     * Authorization: staff/admin required. Errors: throws if unauthorized, or (from the workflow) if the
     * claim is missing or cannot be denied.
     */
    @PostMapping("/claims/{id}/deny")
    public Claim denyClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        // Authorize and capture the acting staff/admin.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.denyClaim(id, admin, data);
    }

    /**
     * POST /api/admin/claims/{id}/request-more-info — ask the claimant for additional information.
     *
     * @param id        path variable: the claim's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @param data      optional request body: the message/details requested from the claimant; may be null
     * @return the updated {@link Claim} (transitioned to a "need more info" state); 200 OK
     * Authorization: staff/admin required. Errors: throws if unauthorized, or (from the workflow) if the
     * claim is missing or in an invalid state for this transition.
     */
    @PostMapping("/claims/{id}/request-more-info")
    public Claim requestMoreInfo(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        // Authorize and capture the acting staff/admin.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.requestMoreInfo(id, admin, data);
    }

    /**
     * POST /api/admin/items/{id}/archive — archive a found item (remove it from active inventory).
     *
     * @param id        path variable: the found item's id
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @param data      optional request body: archive reason/notes passed through to the workflow; may be null
     * @return the updated {@link FoundItem} (now archived); 200 OK
     * Authorization: staff/admin required. Errors: throws if unauthorized, or (from the workflow) if the
     * item does not exist.
     */
    @PostMapping("/items/{id}/archive")
    public FoundItem archiveItem(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        // Authorize and capture the acting staff/admin.
        AppUser admin = authorizationService.requireStaffOrAdmin(userEmail);
        return workflow.archiveItem(id, admin, data);
    }

    /**
     * GET /api/admin/users — list every user account.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return all {@link AppUser} records; 200 OK
     * Authorization: ADMIN required (stricter than the staff/admin endpoints above) because the list
     * exposes roles/PII. Throws via the authorization service if the caller is not an admin.
     */
    @GetMapping("/users")
    public List<AppUser> users(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: admin only (not just staff).
        authorizationService.requireAdmin(userEmail);
        return userRepository.findAll();
    }

    /**
     * PATCH /api/admin/users/{id}/role — change a user's role.
     *
     * @param id        path variable: the target user's id
     * @param body      request body containing a {@code "role"} field; accepted values are
     *                  "student", "staff", "admin", or "suspended" (case-insensitive, trimmed)
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return the updated {@link AppUser}; 200 OK
     * Authorization: ADMIN required. Errors: {@link BadRequestException} if the role is missing/invalid;
     * {@link NotFoundException} if no user matches {@code id}.
     */
    @PatchMapping("/users/{id}/role") // HTTP PATCH (partial update) on the user's role
    public AppUser updateUserRole(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorization gate: admin only.
        authorizationService.requireAdmin(userEmail);
        // Normalize the requested role (trim + lowercase); default to "" so invalid/missing values are rejected below.
        String role = body.get("role") != null ? String.valueOf(body.get("role")).trim().toLowerCase() : "";
        // Validate against the allowed role set.
        if (!List.of("student", "staff", "admin", "suspended").contains(role)) {
            throw new BadRequestException("Invalid role.");
        }
        // Load the target user or 404 if absent.
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
        // Apply the new role and stamp the update time, then persist.
        user.setRole(role);
        user.setUpdatedDate(clock.now());
        return userRepository.save(user);
    }

    /**
     * PATCH /api/admin/lost-reports/{reportId}/matches/{foundItemId} — record a staff decision on a
     * suggested match between a lost report and a found item.
     *
     * @param reportId    path variable: the lost report's id
     * @param foundItemId path variable: the candidate found item's id
     * @param body        request body containing a {@code "decision"} field: "confirmed", "rejected",
     *                    or "linked" (case-insensitive, trimmed)
     * @param userEmail   caller identity from the {@code X-Demo-User-Email} header; must resolve to staff/admin
     * @return the updated {@link LostReport} after the decision is applied; 200 OK
     * Authorization: staff/admin required. Errors: {@link BadRequestException} if the decision value is
     * missing/invalid; further errors may surface from {@link MatchmakingService#decideMatch}.
     */
    @PatchMapping("/lost-reports/{reportId}/matches/{foundItemId}")
    public LostReport decideLostReportMatch(
            @PathVariable String reportId,
            @PathVariable String foundItemId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorization gate: staff or admin only.
        authorizationService.requireStaffOrAdmin(userEmail);
        // Normalize the decision and validate it is one of the allowed values.
        String decision = body.get("decision") != null ? String.valueOf(body.get("decision")).trim().toLowerCase() : "";
        if (!List.of("confirmed", "rejected", "linked").contains(decision)) {
            throw new BadRequestException("Decision must be confirmed, rejected, or linked.");
        }
        // Delegate the actual match state transition to the matchmaking service.
        return matchmakingService.decideMatch(reportId, foundItemId, decision);
    }

    /**
     * GET /api/admin/audit — full audit log (admin-only variant of {@link #auditLogs}).
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return the list of {@link AuditLog} records; 200 OK
     * Authorization: ADMIN required. Throws via the authorization service if the caller is not an admin.
     */
    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: admin only.
        authorizationService.requireAdmin(userEmail);
        return workflow.listAuditLogs();
    }

    /**
     * GET /api/admin/patterns — analytics summary of found items grouped by category and status.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return a map with {@code total_items} (count), {@code by_category} (category → count), and
     *         {@code by_status} (status → count); 200 OK
     * Authorization: ADMIN required. Items with a null category/status are bucketed under "unknown".
     */
    @GetMapping("/patterns")
    public Map<String, Object> patterns(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Authorization gate: admin only.
        authorizationService.requireAdmin(userEmail);
        // Load all found items, then aggregate counts in memory.
        List<FoundItem> items = foundItemRepository.findAll();
        // Group items by category, counting each bucket (null category → "unknown").
        Map<String, Long> byCategory = items.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory() == null ? "unknown" : item.getCategory(), Collectors.counting()));
        // Group items by status, counting each bucket (null status → "unknown").
        Map<String, Long> byStatus = items.stream()
                .collect(Collectors.groupingBy(item -> item.getStatus() == null ? "unknown" : item.getStatus(), Collectors.counting()));
        // Assemble the response in insertion order (LinkedHashMap) for stable JSON output.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_items", items.size());
        result.put("by_category", byCategory);
        result.put("by_status", byStatus);
        return result;
    }
}
