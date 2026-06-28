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

/**
 * Manages system-wide configuration settings.
 *
 * <p>Serves the base route {@code /api/admin/settings}. Reading/writing arbitrary
 * settings is admin-only (verified via {@link DemoAuthorizationService} using the
 * {@code X-Demo-User-Email} header). Two read endpoints — the item categories list
 * and pickup-location info — are intentionally PUBLIC (no auth) because the
 * student-facing UI needs them. Settings are persisted by {@link SystemSettingService}
 * as string key/value pairs; JSON-typed values (e.g. the categories array) are
 * (de)serialized with {@link ObjectMapper}.</p>
 */
@RestController // REST controller: handler return values are serialized to the response body
@RequestMapping("/api/admin/settings") // base route for all settings endpoints
public class AdminSettingsController {
    // Jackson type token used to deserialize the stored "categories" setting back into a List<String>.
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    // Fallback category list returned when no "categories" setting has been stored (or it fails to parse).
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "electronics", "clothing", "bags_cases", "personal_items",
            "food_containers", "books_stationery", "keys", "jewelry",
            "sports_equipment", "musical_instruments", "other"
    );

    // Persistence/lookup of system settings as string key/value pairs.
    private final SystemSettingService service;
    // Resolves the caller and enforces admin authorization on the write/read-all endpoints.
    private final DemoAuthorizationService authorizationService;
    // JSON (de)serialization for settings whose value is structured (e.g. the categories list).
    private final ObjectMapper objectMapper;

    /** Constructor injection of the setting service, authorization service, and JSON mapper. */
    public AdminSettingsController(
            SystemSettingService service,
            DemoAuthorizationService authorizationService,
            ObjectMapper objectMapper) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/admin/settings — return every stored setting as a key/value map.
     *
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return all settings as a {@code Map<String,String>}; 200 OK
     * Authorization: ADMIN required. Throws via the authorization service if the caller is not an admin.
     */
    @GetMapping // HTTP GET on the base route
    public Map<String, String> getAll(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorization gate: admin only.
        authorizationService.requireAdmin(userEmail);
        return service.getAllAsMap();
    }

    /**
     * PUT /api/admin/settings/{key} — create or update a single setting (upsert).
     *
     * @param key       path variable: the setting key to write
     * @param body      request body containing a {@code "value"} field (required); non-string values are
     *                  coerced to their {@code toString()} form before being stored
     * @param userEmail caller identity from the {@code X-Demo-User-Email} header; must resolve to a full admin
     * @return a map echoing the saved {@code key} and {@code value}; 200 OK
     * Authorization: ADMIN required (the admin's email is recorded as the modifier).
     * Errors: {@link BadRequestException} if {@code value} is missing/null.
     */
    @PutMapping("/{key}") // HTTP PUT (idempotent upsert) for the setting identified by {key}
    public Map<String, Object> upsert(
            @PathVariable String key,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorize and capture the admin so the change can be attributed.
        AppUser admin = authorizationService.requireAdmin(userEmail);
        Object val = body.get("value");
        // The value field is mandatory.
        if (val == null) {
            throw new BadRequestException("value is required.");
        }
        // Store as a String: pass through if already a String, otherwise stringify.
        String value = val instanceof String s ? s : val.toString();
        service.upsert(key, value, admin.getEmail());
        return Map.of("key", key, "value", value);
    }

    /**
     * GET /api/admin/settings/categories — list the configured item categories.
     *
     * <p>PUBLIC endpoint (no authorization) — the header param is accepted but unused.</p>
     *
     * @param userEmail accepted from the {@code X-Demo-User-Email} header but ignored (no auth performed)
     * @return the stored categories list, or {@link #DEFAULT_CATEGORIES} if none is stored or the stored
     *         JSON cannot be parsed; 200 OK
     */
    @GetMapping("/categories")
    public List<String> categories(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Categories are publicly readable — no auth required
        // Read the raw stored JSON (null if unset).
        String stored = service.get("categories", null);
        if (stored == null) {
            return DEFAULT_CATEGORIES;
        }
        try {
            // Deserialize the stored JSON array back into a List<String>.
            return objectMapper.readValue(stored, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            // Corrupt/invalid stored value: fall back to the defaults rather than erroring.
            return DEFAULT_CATEGORIES;
        }
    }

    /**
     * GET /api/admin/settings/pickup-locations — return the configured pickup location and hours.
     *
     * <p>PUBLIC endpoint (no authorization) — the header param is accepted but unused.</p>
     *
     * @param userEmail accepted from the {@code X-Demo-User-Email} header but ignored (no auth performed)
     * @return a map with {@code "location"} and {@code "hours"}, each falling back to a sensible default
     *         when the corresponding setting is unset; 200 OK
     */
    @GetMapping("/pickup-locations")
    public Map<String, String> pickupLocations(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Read each setting with a built-in default if it has not been configured.
        String location = service.get("pickup.location", "PVHS Main Office pickup station");
        String hours = service.get("pickup.hours", "School days, 8:00 AM-3:30 PM");
        return Map.of("location", location, "hours", hours);
    }
}
