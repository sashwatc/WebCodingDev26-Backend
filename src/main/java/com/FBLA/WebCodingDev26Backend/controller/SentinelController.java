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

/** Loss Sentinel prevention alerts for the admin dashboard (read + triage actions). */
@RestController
@RequestMapping("/api/sentinel")
public class SentinelController {
    private final LossSentinelService sentinel;
    private final DemoAuthorizationService authorization;

    public SentinelController(LossSentinelService sentinel, DemoAuthorizationService authorization) {
        this.sentinel = sentinel;
        this.authorization = authorization;
    }

    @GetMapping("/alerts")
    public List<PreventionAlert> alerts(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.list();
    }

    @PostMapping("/recompute")
    public PatternReviewResult recompute(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.recompute();
    }

    @PatchMapping("/alerts/{id}")
    public PreventionAlert update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.update(id, data, admin.getEmail());
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public PreventionAlert acknowledge(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.acknowledge(id, admin.getEmail());
    }

    @PostMapping("/alerts/{id}/dismiss")
    public PreventionAlert dismiss(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.dismiss(id, admin.getEmail(), data == null ? Map.of() : data);
    }

    @PostMapping("/alerts/{id}/resolve")
    public PreventionAlert resolve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorization.requireStaffOrAdmin(userEmail);
        return sentinel.resolve(id, admin.getEmail(), data == null ? Map.of() : data);
    }

    @GetMapping("/alerts/{id}/source-reports")
    public List<LostReport> sourceReports(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return sentinel.sourceReports(id);
    }
}
