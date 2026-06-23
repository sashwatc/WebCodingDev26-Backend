package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.RecoveryCenterResponse;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.RecoveryCaseService;
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
@RequestMapping("/api")
public class RecoveryCaseController {
    private final RecoveryCaseService service;
    private final DemoAuthorizationService authorizationService;

    public RecoveryCaseController(RecoveryCaseService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/recovery-cases")
    public List<RecoveryCase> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return service.list();
    }

    @GetMapping("/admin/recovery-center")
    public RecoveryCenterResponse center(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return service.center();
    }

    @GetMapping("/recovery-cases/{id}")
    public RecoveryCase get(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return service.get(id);
    }

    @GetMapping("/recovery-cases/lost-reports/{lostReportId}")
    public RecoveryCase getByLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return service.getByLostReport(lostReportId);
    }

    @PostMapping("/admin/recovery-cases")
    public RecoveryCase createWithLostReport(
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.createFromLostReportData(data, admin.getEmail());
    }

    @PostMapping("/admin/recovery-cases/lost-reports/{lostReportId}")
    public RecoveryCase createFromLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.createFromLostReport(lostReportId, admin.getEmail());
    }

    @PostMapping("/recovery-cases/lost-reports/{lostReportId}/refresh")
    public RecoveryCase refresh(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.refreshForLostReport(lostReportId, admin.getEmail());
    }

    @PatchMapping("/recovery-cases/{id}")
    public RecoveryCase update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.update(id, data, admin.getEmail());
    }

    @PostMapping("/recovery-cases/{id}/assign")
    public RecoveryCase assign(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.assignStaff(id, data, admin.getEmail());
    }

    @GetMapping("/recovery-cases/{id}/missions")
    public List<RecoveryMission> missions(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return service.missionsForCase(id);
    }

    @PostMapping("/recovery-cases/{id}/missions")
    public RecoveryMission createMission(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.createMission(id, data, admin.getEmail());
    }

    @PatchMapping("/recovery-missions/{id}")
    public RecoveryMission updateMission(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return service.updateMission(id, data, admin.getEmail());
    }
}
