package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.SavedSearch;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.SavedSearchService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {
    private final SavedSearchService service;
    private final DemoAuthorizationService authorizationService;

    @Autowired
    public SavedSearchController(SavedSearchService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<SavedSearch> list(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        return service.findByUserId(user.getEmail());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearch create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        String name = body.get("name") != null ? String.valueOf(body.get("name")).trim() : "";
        if (name.isBlank()) {
            throw new BadRequestException("Name is required.");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> filters = body.get("filters") instanceof Map<?, ?> rawMap
                ? (Map<String, String>) rawMap.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                e -> String.valueOf(e.getKey()),
                                e -> String.valueOf(e.getValue()),
                                (a, b) -> b,
                                java.util.LinkedHashMap::new))
                : new java.util.LinkedHashMap<>();
        Boolean alertsEnabled = body.get("alertsEnabled") instanceof Boolean b ? b
                : body.get("alerts_enabled") instanceof Boolean b2 ? b2 : false;
        return service.create(user.getEmail(), name, filters, alertsEnabled);
    }

    @PatchMapping("/{id}")
    public SavedSearch update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        String name = body.get("name") != null ? String.valueOf(body.get("name")).trim() : null;
        Boolean alertsEnabled = body.get("alertsEnabled") instanceof Boolean b ? b
                : body.get("alerts_enabled") instanceof Boolean b2 ? b2 : null;
        return service.update(id, user.getEmail(), name, alertsEnabled);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        service.delete(id, user.getEmail());
        return Map.of("success", true);
    }
}
