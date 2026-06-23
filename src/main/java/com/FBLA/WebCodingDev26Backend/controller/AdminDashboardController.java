package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.service.AdminWorkflowService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminDashboardController(AdminWorkflowService workflow, DemoAuthorizationService authorizationService) {
        this.workflow = workflow;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.dashboard();
    }

    @GetMapping("/lost-reports")
    public List<?> lostReports(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.listLostReports();
    }

    @GetMapping("/claims")
    public List<Claim> claims(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.listClaims();
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.listAuditLogs();
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return workflow.listNotifications();
    }

    @PostMapping("/claims/{id}/approve")
    public Claim approveClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return workflow.approveClaim(id, admin, data);
    }

    @PostMapping("/claims/{id}/deny")
    public Claim denyClaim(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return workflow.denyClaim(id, admin, data);
    }

    @PostMapping("/items/{id}/archive")
    public FoundItem archiveItem(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestBody(required = false) Map<String, Object> data
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return workflow.archiveItem(id, admin, data);
    }
}
