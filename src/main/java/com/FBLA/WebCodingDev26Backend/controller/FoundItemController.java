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

/**
 * REST controller for found items — the catalog of items that have been turned in.
 *
 * <p>Base route: {@code /api/items} ({@link RequestMapping}). Read endpoints (list, detail,
 * share-link) are public; write endpoints apply staff/admin authorization. Creation is open to
 * the public (anyone can report a found item) but staff get elevated create privileges.
 *
 * <p>Collaborators: delegates all business logic and persistence to {@link FoundItemService};
 * uses {@link DemoAuthorizationService} to resolve the caller's role from the demo email header;
 * uses the configured front-end base URL to build shareable links.
 */
@RestController
@RequestMapping("/api/items")
public class FoundItemController {
    // Service holding all found-item business logic, filtering, mapping and persistence.
    private final FoundItemService service;
    // Resolves the caller's role (staff/admin vs. public) from the demo email header; may be
    // null in the test-only constructor below.
    private final DemoAuthorizationService authorizationService;
    // Base URL of the front-end app, used to construct public share links for an item.
    private final String frontendUrl;

    /**
     * Primary constructor used by Spring. {@link Autowired} marks it for dependency injection.
     * The front-end URL is read from property {@code app.frontend.url}, defaulting to
     * {@code https://pvhs-lostfound.app} when unset ({@link Value} default syntax).
     */
    @Autowired
    public FoundItemController(
            FoundItemService service,
            DemoAuthorizationService authorizationService,
            @Value("${app.frontend.url:https://pvhs-lostfound.app}") String frontendUrl) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.frontendUrl = frontendUrl;
    }

    // Package-private constructor for test compatibility: builds the controller with no
    // authorization service (so role checks are skipped) and the default front-end URL.
    FoundItemController(FoundItemService service) {
        this(service, null, "https://pvhs-lostfound.app");
    }

    /**
     * GET {@code /api/items} — public, paginated, filtered list of found items.
     *
     * <p>All query params are optional filters; the service applies whichever are provided.
     *
     * @param q free-text search query.
     * @param category item category filter.
     * @param color item color filter.
     * @param location location filter.
     * @param status item status filter.
     * @param dateFrom inclusive lower bound on the relevant date.
     * @param dateTo inclusive upper bound on the relevant date.
     * @param sortBy sort order, default {@code "newest"}.
     * @param page zero-based page index, default {@code 0}.
     * @param size page size, default {@code 200}.
     * @return 200 OK with the list of items for the requested page (the {@code "items"} entry
     *         extracted from the service's filtered result map). {@link SuppressWarnings} silences
     *         the unchecked cast of that map entry to {@code List<Object>}.
     */
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
        // Delegate filtering/sorting/paging to the service; it returns a map and we hand back
        // only the "items" page slice (total counts etc. are dropped for this endpoint).
        Map<String, Object> result = service.listFiltered(q, category, color, location, status, dateFrom, dateTo, sortBy, page, size);
        return (List<Object>) result.get("items");
    }

    /**
     * GET {@code /api/items/{id}} — public detail view of a single found item.
     *
     * @param id path variable: the found item's id.
     * @return 200 OK with the public detail projection. The service throws (typically 404) when
     *         the item does not exist or is not publicly viewable.
     */
    @GetMapping("/{id}")
    public PublicFoundItemResponse get(@PathVariable String id) {
        return service.getPublicDetail(id);
    }

    /**
     * GET {@code /api/items/{id}/share-link} — build social/share metadata for an item.
     *
     * @param id path variable: the found item's id.
     * @return 200 OK with a map containing the public {@code url} (front-end base + item path),
     *         the item {@code title}, and a {@code description} truncated to 120 chars (with an
     *         ellipsis) — or an empty string when the item has no description. The service throws
     *         (typically 404) when the item is missing/not public.
     */
    @GetMapping("/{id}/share-link")
    public Map<String, Object> shareLink(@PathVariable String id) {
        PublicFoundItemResponse item = service.getPublic(id);
        // Compose the shareable front-end URL for this item.
        String url = frontendUrl + "/items/" + id;
        // Truncate long descriptions to 120 chars for the share preview; empty when none.
        String description = item.description() != null
                ? (item.description().length() > 120 ? item.description().substring(0, 120) + "..." : item.description())
                : "";
        // LinkedHashMap preserves insertion order so the JSON keys come out url/title/description.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", item.title());
        result.put("description", description);
        return result;
    }

    /**
     * POST {@code /api/items} — report (create) a new found item. Open to the public.
     *
     * @param data request body: a map of the new item's fields.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller; used only
     *        to detect elevated (staff/admin) creation privileges, not required.
     * @return 201 Created ({@link ResponseStatus}) with the persisted {@link FoundItem}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoundItem create(
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Staff/admin callers may set privileged fields on create; the service enforces what a
        // non-staff caller is allowed to provide. Null authorizationService (tests) ⇒ not staff.
        boolean isStaff = authorizationService != null && authorizationService.isStaffOrAdmin(userEmail);
        return service.create(data, isStaff);
    }

    /**
     * PATCH {@code /api/items/{id}} — partially update a found item.
     *
     * @param id path variable: the item to update.
     * @param data request body: the fields to change.
     * @param userEmail optional {@code X-Demo-User-Email} header; determines whether the caller
     *        may edit staff-only fields. The service gates field-level access by this flag.
     * @return 200 OK with the updated {@link FoundItem}.
     */
    @PatchMapping("/{id}")
    public FoundItem update(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Resolve staff status to control which fields the update is permitted to change.
        boolean isStaff = authorizationService != null && authorizationService.isStaffOrAdmin(userEmail);
        return service.update(id, data, isStaff);
    }

    /**
     * PUT {@code /api/items/{id}/status} — change an item's status (a staff/admin moderation action).
     *
     * @param id path variable: the item whose status to change.
     * @param data request body: the status fields to apply.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link FoundItem}.
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin (enforced by
     *         {@code requireStaffOrAdmin}). The update is then performed with staff privileges.
     */
    @PutMapping("/{id}/status")
    public FoundItem updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Status transitions are staff-only — hard-require the role (skipped when service is null in tests).
        if (authorizationService != null) {
            authorizationService.requireStaffOrAdmin(userEmail);
        }
        // Pass isStaff=true since access has already been authorized above.
        return service.update(id, data, true);
    }

    /**
     * DELETE {@code /api/items/{id}} — delete/archive a found item (destructive moderation, staff/admin only).
     *
     * @param id path variable: the item to delete.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the service's deletion result map.
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin.
     */
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
