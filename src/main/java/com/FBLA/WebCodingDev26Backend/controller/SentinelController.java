package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.LossSentinelService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the "Loss Sentinel" prevention-alert system shown on the admin/staff
 * dashboard (read + triage actions).
 *
 * <p>Base route: {@code /api/sentinel}. Returns JSON. Loss Sentinel analyzes loss reports to surface
 * patterns (e.g. recurring hot-spots) as {@link PreventionAlert}s that staff can acknowledge,
 * dismiss, or resolve.
 *
 * <p>Every endpoint requires a staff or admin caller, identified via the demo
 * {@code X-Demo-User-Email} header.
 *
 * <p>Collaborators: {@link LossSentinelService} (alert computation, persistence, and triage state
 * transitions) and {@link DemoAuthorizationService} (resolves and authorizes the caller).
 */
@RestController // JSON REST controller
@RequestMapping("/api/sentinel") // shared base path for all handlers
public class SentinelController {
    /** Computes and manages prevention alerts and their triage lifecycle. */
    private final LossSentinelService sentinel;
    /** Resolves the caller from the demo header and enforces staff/admin access. */
    private final DemoAuthorizationService authorization;

    /** Constructor injection of the Loss Sentinel service and authorization collaborators. */
    public SentinelController(LossSentinelService sentinel, DemoAuthorizationService authorization) {
        this.sentinel = sentinel;
        this.authorization = authorization;
    }

    /**
     * GET {@code /api/sentinel/alerts} — list all current prevention alerts.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with all {@link PreventionAlert}s.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/alerts")
    public List<PreventionAlert> alerts(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.list();
    }

    /**
     * POST {@code /api/sentinel/recompute} — re-run pattern analysis to regenerate/refresh alerts.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with a {@link PatternReviewResult} summarizing the recomputation.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PostMapping("/recompute")
    public PatternReviewResult recompute(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.recompute();
    }

    /**
     * PATCH {@code /api/sentinel/alerts/{id}} — partially update an alert (generic field edits).
     *
     * @param id the alert id (path variable).
     * @param data loosely-typed map of fields to update.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link PreventionAlert}. The acting staff email is recorded.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PatchMapping("/alerts/{id}")
    public PreventionAlert update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.update(id, data, admin.getEmail());
    }

    /**
     * POST {@code /api/sentinel/alerts/{id}/acknowledge} — mark an alert as acknowledged by staff.
     *
     * @param id the alert id (path variable).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the acknowledged {@link PreventionAlert} (acting staff email recorded).
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PostMapping("/alerts/{id}/acknowledge")
    public PreventionAlert acknowledge(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.acknowledge(id, admin.getEmail());
    }

    /**
     * POST {@code /api/sentinel/alerts/{id}/dismiss} — dismiss an alert (false positive / no action).
     *
     * @param id the alert id (path variable).
     * @param data optional body (e.g. a dismissal reason); defaults to an empty map when absent.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the dismissed {@link PreventionAlert} (acting staff email recorded).
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PostMapping("/alerts/{id}/dismiss")
    public PreventionAlert dismiss(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.dismiss(id, admin.getEmail(), data == null ? Map.of() : data); // null body -> empty map
    }

    /**
     * POST {@code /api/sentinel/alerts/{id}/resolve} — mark an alert resolved (action taken).
     *
     * @param id the alert id (path variable).
     * @param data optional body (e.g. resolution notes); defaults to an empty map when absent.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the resolved {@link PreventionAlert} (acting staff email recorded).
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PostMapping("/alerts/{id}/resolve")
    public PreventionAlert resolve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.resolve(id, admin.getEmail(), data == null ? Map.of() : data); // null body -> empty map
    }

    /**
     * GET {@code /api/sentinel/alerts/{id}/source-reports} — list the loss reports that contributed
     * to (triggered) a given alert, for staff drill-down.
     *
     * @param id the alert id (path variable).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the {@link LostReport}s underpinning the alert.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/alerts/{id}/source-reports")
    public List<LostReport> sourceReports(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.sourceReports(id);
    }
}
