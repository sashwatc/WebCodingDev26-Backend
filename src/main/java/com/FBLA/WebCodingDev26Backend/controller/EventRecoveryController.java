package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PublicEventHubResponse;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.EventRecoveryService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventRecoveryController {
    private final EventRecoveryService eventRecoveryService;
    private final DemoAuthorizationService authorizationService;

    public EventRecoveryController(EventRecoveryService eventRecoveryService, DemoAuthorizationService authorizationService) {
        this.eventRecoveryService = eventRecoveryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/campus-zones")
    public List<CampusZone> zones() {
        return eventRecoveryService.listZones();
    }

    @GetMapping("/api/event-hubs")
    public List<PublicEventHubResponse> hubs() {
        return eventRecoveryService.listPublicHubs();
    }

    @GetMapping("/api/event-hubs/{id}")
    public PublicEventHubResponse hub(@PathVariable String id) {
        return eventRecoveryService.getPublicHub(id);
    }

    @GetMapping("/api/event-hubs/{id}/display-feed")
    public Map<String, Object> displayFeed(@PathVariable String id) {
        return eventRecoveryService.displayFeed(id);
    }

    @PostMapping("/api/admin/event-hubs")
    public EventRecoveryHub createHub(@RequestBody Map<String, Object> data, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        return eventRecoveryService.createHub(data, admin);
    }

    @PatchMapping("/api/admin/event-hubs/{id}")
    public EventRecoveryHub updateHub(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return eventRecoveryService.updateHub(id, data);
    }

    @PostMapping("/api/admin/event-hubs/{id}/activate")
    public EventRecoveryHub activate(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return eventRecoveryService.activate(id);
    }

    @PostMapping("/api/admin/event-hubs/{id}/close")
    public EventRecoveryHub close(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorizationService.requireAdmin(userEmail);
        return eventRecoveryService.close(id);
    }
}
