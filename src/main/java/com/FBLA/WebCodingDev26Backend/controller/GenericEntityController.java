package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.model.LostReport;
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

/**
 * Generic CRUD controller exposing a dynamic, name-addressed entity API.
 *
 * <p>Base route: {@code /api/entities/{entityName}} ({@link RequestMapping}). Instead of one
 * controller per model, a single set of endpoints serves many entity types keyed by the
 * {@code entityName} path segment (e.g. {@code LostReport}, {@code Claim}); persistence is
 * delegated to {@link GenericEntityService}, which resolves the name to the right store.
 *
 * <p>Because this is generic, it layers entity-specific guards on top:
 * <ul>
 *   <li>{@code Claim} records are private — admin-only to list/delete, and admin-only for
 *       privileged field updates (status/notes/review fields).</li>
 *   <li>{@code LostReport} records carry reporter PII that is redacted for non-staff callers
 *       who do not own the record.</li>
 * </ul>
 * {@link DemoAuthorizationService} resolves the caller's role/email from the demo email header.
 */
@RestController
@RequestMapping("/api/entities")
public class GenericEntityController {
    // Claim fields that only an admin may set/change. A PATCH touching any of these (or setting
    // review_status to anything other than "pending") requires admin authorization.
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
    // Generic persistence service: maps an entityName to its repository and performs CRUD.
    private final GenericEntityService service;
    // Resolves caller role/email from the demo email header and enforces admin/staff requirements.
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the generic entity service and the authorization service. */
    public GenericEntityController(GenericEntityService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    /**
     * GET {@code /api/entities/{entityName}} — list all records of the named entity type.
     *
     * @param entityName path variable: the entity type to list (e.g. {@code LostReport}, {@code Claim}).
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the list of records (possibly PII-redacted for LostReport).
     * @throws RuntimeException 403 Forbidden when listing a private entity ({@code Claim}) without admin.
     */
    @GetMapping("/{entityName}")
    public List<?> list(
            @PathVariable String entityName,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Private entities (Claim) require admin even to read.
        requireAdminForPrivateEntities(entityName, userEmail);
        List<?> records = service.list(entityName);
        // For LostReport, hide contact PII from any caller who is not staff/admin.
        if ("LostReport".equals(entityName) && !authorizationService.isStaffOrAdmin(userEmail)) {
            redactLostReportContactInfo(records, authorizationService.resolveEmail(userEmail));
        }
        return records;
    }

    /**
     * Lost reports carry reporter PII (name / email / phone / student id). For
     * non-staff callers, strip those fields from every record the caller does not
     * own, so the public browse and matching keep working without leaking contact
     * details. Records are read-only here (never persisted back), so mutating the
     * in-memory copies is safe.
     */
    private void redactLostReportContactInfo(List<?> records, String callerEmail) {
        // Normalize the caller's email for a case-insensitive ownership comparison.
        String caller = callerEmail == null ? "" : callerEmail.trim().toLowerCase(Locale.ROOT);
        for (Object record : records) {
            // Only LostReport instances carry the PII we redact; skip anything else.
            if (!(record instanceof LostReport report)) {
                continue;
            }
            // The record's owner is identified by its contact email (normalized).
            String owner = report.getContactEmail() == null
                    ? ""
                    : report.getContactEmail().trim().toLowerCase(Locale.ROOT);
            // Redact unless the caller is the owner: an anonymous caller, or one whose email
            // does not match this record, gets the contact fields stripped.
            if (caller.isBlank() || !caller.equals(owner)) {
                report.setContactName(null);
                report.setContactEmail(null);
                report.setContactPhone(null);
                report.setStudentId(null);
            }
        }
    }

    /**
     * POST {@code /api/entities/{entityName}} — create a new record of the named entity type.
     *
     * @param entityName path variable: the entity type to create.
     * @param data request body: the new record's fields.
     * @return 201 Created ({@link ResponseStatus}) with the persisted record.
     */
    @PostMapping("/{entityName}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@PathVariable String entityName, @RequestBody Map<String, Object> data) {
        return service.create(entityName, data);
    }

    /**
     * PATCH {@code /api/entities/{entityName}/{id}} — partially update a record.
     *
     * @param entityName path variable: the entity type being updated.
     * @param id path variable: the record id.
     * @param data request body: the fields to change.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated record.
     * @throws RuntimeException 403 Forbidden when updating privileged Claim fields without admin.
     */
    @PatchMapping("/{entityName}/{id}")
    public Object update(
            @PathVariable String entityName,
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Block non-admins from changing privileged Claim fields (status, review fields, notes).
        requireAdminForPrivilegedClaimUpdate(entityName, data, userEmail);
        return service.update(entityName, id, data);
    }

    /**
     * DELETE {@code /api/entities/{entityName}/{id}} — delete a record.
     *
     * @param entityName path variable: the entity type to delete from.
     * @param id path variable: the record id.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with {@code {"success": <boolean>}} reflecting whether a record was removed.
     * @throws RuntimeException 403 Forbidden when deleting a private entity ({@code Claim}) without admin.
     */
    @DeleteMapping("/{entityName}/{id}")
    public Map<String, Boolean> delete(
            @PathVariable String entityName,
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Private entities (Claim) require admin to delete.
        requireAdminForPrivateEntities(entityName, userEmail);
        return Map.of("success", service.delete(entityName, id));
    }

    /** Enforces admin for entity types treated as private. Currently only {@code Claim} qualifies. */
    private void requireAdminForPrivateEntities(String entityName, String userEmail) {
        if ("Claim".equals(entityName)) {
            authorizationService.requireAdmin(userEmail);
        }
    }

    /**
     * For Claim updates, requires admin if the patch touches any privileged field. Non-Claim
     * entities and null bodies are unaffected (public/self-service fields stay open).
     */
    private void requireAdminForPrivilegedClaimUpdate(String entityName, Map<String, Object> data, String userEmail) {
        if (!"Claim".equals(entityName) || data == null) {
            return;
        }
        // Privileged if ANY field in the patch is a restricted Claim field.
        boolean privileged = data.entrySet().stream()
                .anyMatch(entry -> isPrivilegedClaimUpdateField(entry.getKey(), entry.getValue()));
        if (privileged) {
            authorizationService.requireAdmin(userEmail);
        }
    }

    /**
     * Decides whether a single patch field counts as a privileged Claim update.
     * Special case: {@code review_status} is privileged only when set to a non-blank value other
     * than {@code "pending"} (a user may keep/submit a pending claim, but only admins may approve
     * or otherwise advance it). All other restricted fields are matched against the static set,
     * by both the raw key and a normalized (trimmed, lower-cased) key.
     */
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
