package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class FoundItemController {
    private final FoundItemService service;
    private final DemoAuthorizationService authorizationService;
    private final String frontendUrl;

    @Autowired
    public FoundItemController(
            FoundItemService service,
            DemoAuthorizationService authorizationService,
            @Value("${app.frontend.url:https://pvhs-lostfound.app}") String frontendUrl) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.frontendUrl = frontendUrl;
    }

    // Package-private constructor for test compatibility
    FoundItemController(FoundItemService service) {
        this(service, null, "https://pvhs-lostfound.app");
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public List<Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "newest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        Map<String, Object> result = service.listFiltered(q, category, color, location, status, dateFrom, dateTo, sortBy, page, size);
        return (List<Object>) result.get("items");
    }

    @GetMapping("/{id}")
    public PublicFoundItemResponse get(@PathVariable String id) {
        return service.getPublic(id);
    }

    @GetMapping("/{id}/share-link")
    public Map<String, Object> shareLink(@PathVariable String id) {
        PublicFoundItemResponse item = service.getPublic(id);
        String url = frontendUrl + "/items/" + id;
        String description = item.description() != null
                ? (item.description().length() > 120 ? item.description().substring(0, 120) + "..." : item.description())
                : "";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", item.title());
        result.put("description", description);
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoundItem create(
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        boolean isStaff = authorizationService != null && authorizationService.isStaffOrAdmin(userEmail);
        return service.create(data, isStaff);
    }

    @PatchMapping("/{id}")
    public FoundItem update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        boolean isStaff = authorizationService != null && authorizationService.isStaffOrAdmin(userEmail);
        return service.update(id, data, isStaff);
    }

    @PutMapping("/{id}/status")
    public FoundItem updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        if (authorizationService != null) {
            authorizationService.requireStaffOrAdmin(userEmail);
        }
        return service.update(id, data, true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Deleting/archiving an item is a destructive moderation action — staff/admin only.
        if (authorizationService != null) {
            authorizationService.requireStaffOrAdmin(userEmail);
        }
        return service.delete(id);
    }
}
