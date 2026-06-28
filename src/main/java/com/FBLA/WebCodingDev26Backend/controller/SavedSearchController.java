package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.SavedSearch;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.SavedSearchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * REST controller for a user's saved searches (named browse filters that can optionally trigger
 * alerts when new matching items appear).
 *
 * <p>Base route: {@code /api/saved-searches}. Returns JSON. Every endpoint requires a signed-in
 * user, identified via the demo {@code X-Demo-User-Email} header; saved searches are scoped to and
 * owned by that user.
 *
 * <p>Collaborators: {@link SavedSearchService} (business logic + persistence) and
 * {@link DemoAuthorizationService} (resolves the current user from the header).
 */
@RestController // JSON REST controller
@RequestMapping("/api/saved-searches") // shared base path for all handlers
public class SavedSearchController {
    /** Encapsulates saved-search creation/update/deletion and ownership-scoped lookups. */
    private final SavedSearchService service;
    /** Resolves the calling user from the {@code X-Demo-User-Email} header. */
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the service and authorization collaborators. */
    public SavedSearchController(SavedSearchService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    /**
     * GET {@code /api/saved-searches} — list the current user's saved searches.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the user's {@link SavedSearch} records.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
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

    /**
     * POST {@code /api/saved-searches} — create a saved search for the current user.
     *
     * <p>Reads from a loosely-typed JSON body:
     * <ul>
     *   <li>{@code name} (required): display name, trimmed; blank -> 400.</li>
     *   <li>{@code filters} (optional): a map of filter key/value pairs; coerced to
     *       {@code Map<String,String>} preserving insertion order; absent/non-map -> empty map.</li>
     *   <li>{@code alertsEnabled} / {@code alerts_enabled} (optional): whether to alert on new
     *       matches; defaults to {@code false}.</li>
     * </ul>
     *
     * @param body loosely-typed request body (see above).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 201 CREATED with the persisted {@link SavedSearch}.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     * @throws BadRequestException (400) if {@code name} is missing/blank.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // success returns HTTP 201
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
        // Coerce the raw "filters" object into an ordered Map<String,String>; on key collision keep
        // the later value, and use a LinkedHashMap so filter order is preserved. Absent/non-map -> {}.
        Map<String, String> filters = body.get("filters") instanceof Map<?, ?> rawMap
                ? rawMap.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                e -> String.valueOf(e.getKey()),
                                e -> String.valueOf(e.getValue()),
                                (a, b) -> b,
                                LinkedHashMap::new))
                : new LinkedHashMap<>();
        // Accept either camelCase or snake_case for the alerts flag; default off.
        Boolean alertsEnabled = body.get("alertsEnabled") instanceof Boolean b ? b
                : body.get("alerts_enabled") instanceof Boolean b2 ? b2 : false;
        return service.create(user.getEmail(), name, filters, alertsEnabled);
    }

    /**
     * PATCH {@code /api/saved-searches/{id}} — partially update a saved search owned by the caller.
     *
     * <p>Only provided fields change: {@code name} (trimmed; null leaves it unchanged) and the
     * {@code alertsEnabled}/{@code alerts_enabled} toggle (null leaves it unchanged). Ownership is
     * enforced inside the service via the user's email.
     *
     * @param id the saved-search id (path variable).
     * @param body loosely-typed partial update body.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link SavedSearch}.
     * @throws ForbiddenException (403) if no signed-in user can be resolved (and the service may
     *         reject if the search is not owned by the caller / not found).
     */
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
        // null name -> leave unchanged; otherwise trimmed new name.
        String name = body.get("name") != null ? String.valueOf(body.get("name")).trim() : null;
        // null alerts flag -> leave unchanged; accepts camelCase or snake_case.
        Boolean alertsEnabled = body.get("alertsEnabled") instanceof Boolean b ? b
                : body.get("alerts_enabled") instanceof Boolean b2 ? b2 : null;
        return service.update(id, user.getEmail(), name, alertsEnabled);
    }

    /**
     * DELETE {@code /api/saved-searches/{id}} — delete a saved search owned by the caller.
     *
     * @param id the saved-search id (path variable).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with {@code {success:true}}.
     * @throws ForbiddenException (403) if no signed-in user can be resolved (the service enforces
     *         ownership via the user's email).
     */
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
