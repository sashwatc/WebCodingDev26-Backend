package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.DemoScenarioResponse;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.DemoScenarioService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/demo-scenarios")
public class DemoScenarioController {
    private final DemoScenarioService scenarios;
    private final DemoAuthorizationService authorizationService;

    public DemoScenarioController(DemoScenarioService scenarios, DemoAuthorizationService authorizationService) {
        this.scenarios = scenarios;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{scenario}")
    public DemoScenarioResponse create(
            @PathVariable String scenario,
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return scenarios.create(scenario, data, admin.getEmail());
    }

    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(
            @RequestBody(required = false) Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return scenarios.cleanup(data, admin.getEmail());
    }
}
