package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.AppUser;
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
    public List<PreventionAlert> recompute(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
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
}
