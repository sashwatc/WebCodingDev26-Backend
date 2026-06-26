package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.SystemSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "electronics", "clothing", "bags_cases", "personal_items",
            "food_containers", "books_stationery", "keys", "jewelry",
            "sports_equipment", "musical_instruments", "other"
    );

    private final SystemSettingService service;
    private final DemoAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public AdminSettingsController(
            SystemSettingService service,
            DemoAuthorizationService authorizationService,
            ObjectMapper objectMapper) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, String> getAll(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireAdmin(userEmail);
        return service.getAllAsMap();
    }

    @PutMapping("/{key}")
    public Map<String, Object> upsert(
            @PathVariable String key,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser admin = authorizationService.requireAdmin(userEmail);
        Object val = body.get("value");
        if (val == null) {
            throw new BadRequestException("value is required.");
        }
        String value = val instanceof String s ? s : val.toString();
        service.upsert(key, value, admin.getEmail());
        return Map.of("key", key, "value", value);
    }

    @GetMapping("/categories")
    public List<String> categories(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Categories are publicly readable — no auth required
        String stored = service.get("categories", null);
        if (stored == null) {
            return DEFAULT_CATEGORIES;
        }
        try {
            return objectMapper.readValue(stored, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return DEFAULT_CATEGORIES;
        }
    }

    @GetMapping("/pickup-locations")
    public Map<String, String> pickupLocations(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        String location = service.get("pickup.location", "PVHS Main Office pickup station");
        String hours = service.get("pickup.hours", "School days, 8:00 AM-3:30 PM");
        return Map.of("location", location, "hours", hours);
    }
}
