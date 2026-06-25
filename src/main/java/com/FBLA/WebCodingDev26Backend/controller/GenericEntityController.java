package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.service.GenericEntityService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api/entities")
public class GenericEntityController {
    private static final Set<String> PRIVILEGED_CLAIM_UPDATE_FIELDS = Set.of(
            "status",
            "admin_notes",
            "adminNotes",
            "reviewed_by",
            "reviewedBy",
            "reviewed_at",
            "reviewedAt",
            "received_confirmed_at",
            "receivedConfirmedAt"
    );
    private final GenericEntityService service;
    private final DemoAuthorizationService authorizationService;

    public GenericEntityController(GenericEntityService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{entityName}")
    public List<?> list(
            @PathVariable String entityName,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        requireAdminForPrivateEntities(entityName, userEmail);
        return service.list(entityName);
    }

    @PostMapping("/{entityName}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@PathVariable String entityName, @RequestBody Map<String, Object> data) {
        return service.create(entityName, data);
    }

    @PatchMapping("/{entityName}/{id}")
    public Object update(
            @PathVariable String entityName,
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        requireAdminForPrivilegedClaimUpdate(entityName, data, userEmail);
        return service.update(entityName, id, data);
    }

    @DeleteMapping("/{entityName}/{id}")
    public Map<String, Boolean> delete(
            @PathVariable String entityName,
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        requireAdminForPrivateEntities(entityName, userEmail);
        return Map.of("success", service.delete(entityName, id));
    }

    private void requireAdminForPrivateEntities(String entityName, String userEmail) {
        if ("Claim".equals(entityName)) {
            authorizationService.requireAdmin(userEmail);
        }
    }

    private void requireAdminForPrivilegedClaimUpdate(String entityName, Map<String, Object> data, String userEmail) {
        if (!"Claim".equals(entityName) || data == null) {
            return;
        }
        boolean privileged = data.entrySet().stream()
                .anyMatch(entry -> isPrivilegedClaimUpdateField(entry.getKey(), entry.getValue()));
        if (privileged) {
            authorizationService.requireAdmin(userEmail);
        }
    }

    private boolean isPrivilegedClaimUpdateField(String key, Object value) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if ("review_status".equals(normalizedKey) || "reviewstatus".equals(normalizedKey)) {
            String reviewStatus = value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
            return !reviewStatus.isBlank() && !"pending".equals(reviewStatus);
        }
        return PRIVILEGED_CLAIM_UPDATE_FIELDS.contains(key)
                || PRIVILEGED_CLAIM_UPDATE_FIELDS.contains(normalizedKey);
    }
}
