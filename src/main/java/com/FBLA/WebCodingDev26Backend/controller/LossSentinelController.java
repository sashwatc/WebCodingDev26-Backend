package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PatternReviewResult;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
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

@RestController
@RequestMapping("/api/sentinel")
public class LossSentinelController {
    private final LossSentinelService lossSentinelService;
    private final DemoAuthorizationService authorizationService;

    public LossSentinelController(LossSentinelService lossSentinelService, DemoAuthorizationService authorizationService) {
        this.lossSentinelService = lossSentinelService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/alerts")
    public List<PreventionAlert> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return lossSentinelService.list();
    }

    @PostMapping("/recompute")
    public PatternReviewResult recompute(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return lossSentinelService.recompute();
    }

    @PatchMapping("/alerts/{id}")
    public PreventionAlert update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return lossSentinelService.update(id, data, admin.getEmail());
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public PreventionAlert acknowledge(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return lossSentinelService.acknowledge(id, admin.getEmail());
    }

    @PostMapping("/alerts/{id}/dismiss")
    public PreventionAlert dismiss(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return lossSentinelService.dismiss(id, admin.getEmail(), data);
    }

    @PostMapping("/alerts/{id}/resolve")
    public PreventionAlert resolve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return lossSentinelService.resolve(id, admin.getEmail(), data);
    }

    @GetMapping("/alerts/{id}/source-reports")
    public List<LostReport> sourceReports(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return lossSentinelService.sourceReports(id);
    }

    @PostMapping("/alerts/{id}/mission")
    public RecoveryMission createMission(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return lossSentinelService.createMissionFromAlert(id, admin.getEmail());
    }
}
