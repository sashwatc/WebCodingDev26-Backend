package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import com.FBLA.WebCodingDev26Backend.service.RecoveryCaseService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecoveryCaseController {
    private final RecoveryCaseService service;

    public RecoveryCaseController(RecoveryCaseService service) {
        this.service = service;
    }

    @GetMapping("/recovery-cases")
    public List<RecoveryCase> list() {
        return service.list();
    }

    @GetMapping("/recovery-cases/{id}")
    public RecoveryCase get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/recovery-cases/lost-reports/{lostReportId}")
    public RecoveryCase getByLostReport(@PathVariable String lostReportId) {
        return service.getByLostReport(lostReportId);
    }

    @PostMapping("/recovery-cases/lost-reports/{lostReportId}/refresh")
    public RecoveryCase refresh(@PathVariable String lostReportId) {
        return service.refreshForLostReport(lostReportId);
    }

    @PatchMapping("/recovery-cases/{id}")
    public RecoveryCase update(@PathVariable String id, @RequestBody Map<String, Object> data) {
        return service.update(id, data);
    }

    @GetMapping("/recovery-cases/{id}/missions")
    public List<RecoveryMission> missions(@PathVariable String id) {
        return service.missionsForCase(id);
    }

    @PatchMapping("/recovery-missions/{id}")
    public RecoveryMission updateMission(@PathVariable String id, @RequestBody Map<String, Object> data) {
        return service.updateMission(id, data);
    }
}
