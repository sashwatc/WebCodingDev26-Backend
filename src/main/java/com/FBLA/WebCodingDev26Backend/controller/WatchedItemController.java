package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.WatchedItem;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.WatchedItemRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for a user's watched ("saved") found items.
 *
 * <p>Base route: {@code /api/watched-items}. Returns JSON. Lets a signed-in user follow specific
 * found items so they appear on their dashboard / can be notified about them.
 *
 * <p>Every endpoint requires a signed-in user, identified via the demo {@code X-Demo-User-Email}
 * header; watched items are scoped to and owned by that user.
 *
 * <p>Collaborators: {@link WatchedItemRepository} (the watch records), {@link FoundItemRepository}
 * (validates and hydrates referenced items), and {@link DemoAuthorizationService} (caller resolution).
 */
@RestController // JSON REST controller
@RequestMapping("/api/watched-items") // shared base path for all handlers
public class WatchedItemController {
    /** The user's watch records (each links a user to a found item). */
    private final WatchedItemRepository watchedItems;
    /** Used to validate that a watched found item exists and to hydrate its public details. */
    private final FoundItemRepository foundItems;
    /** Resolves the calling user from the {@code X-Demo-User-Email} header. */
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the repositories and authorization collaborators. */
    public WatchedItemController(
            WatchedItemRepository watchedItems,
            FoundItemRepository foundItems,
            DemoAuthorizationService authorizationService) {
        this.watchedItems = watchedItems;
        this.foundItems = foundItems;
        this.authorizationService = authorizationService;
    }

    /**
     * GET {@code /api/watched-items} — list the caller's watched items with their item details.
     *
     * <p>Each entry is a map of {@code {watchedItem, item}}, where {@code item} is the public view of
     * the referenced found item and is omitted if that item no longer exists.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the list of watched-item entries.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
    @GetMapping
    public List<Map<String, Object>> list(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        return watchedItems.findByUserId(user.getEmail()).stream()
                .map(wi -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("watchedItem", wi);
                    foundItems.findById(wi.getFoundItemId()).ifPresent(item ->
                            entry.put("item", PublicFoundItemResponse.from(item)));
                    return entry;
                })
                .toList();
    }

    /**
     * POST {@code /api/watched-items} — start watching a found item.
     *
     * <p>Reads {@code foundItemId} (or {@code found_item_id}) from the body. Validates the referenced
     * found item exists, then creates a watch record. The operation is idempotent: if the user
     * already watches the item, the existing record is returned instead of creating a duplicate.
     *
     * @param body request body containing the found item id.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 201 CREATED with the {@link WatchedItem} (existing or newly created).
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     * @throws BadRequestException (400) if {@code foundItemId} is missing/blank.
     * @throws NotFoundException (404) if no found item has the given id.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // success returns HTTP 201
    public WatchedItem watch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        // Accept either camelCase or snake_case key for the found item id.
        String foundItemId = body.get("foundItemId") != null ? String.valueOf(body.get("foundItemId")).trim()
                : (body.get("found_item_id") != null ? String.valueOf(body.get("found_item_id")).trim() : "");
        if (foundItemId.isBlank()) {
            throw new BadRequestException("foundItemId is required.");
        }
        foundItems.findById(foundItemId).orElseThrow(() -> new NotFoundException("Found item not found")); // 404 if item gone
        // Idempotent: return existing if already watched
        return watchedItems.findByUserIdAndFoundItemId(user.getEmail(), foundItemId).orElseGet(() -> {
            WatchedItem wi = new WatchedItem();
            // Generate a short, prefixed id from a random UUID (first 10 hex chars, dashes stripped).
            wi.setId("wi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            wi.setUserId(user.getEmail());
            wi.setFoundItemId(foundItemId);
            wi.setCreatedAt(Instant.now().toString()); // ISO-8601 creation timestamp
            return watchedItems.save(wi);
        });
    }

    /**
     * DELETE {@code /api/watched-items/{foundItemId}} — stop watching a found item.
     *
     * <p>Removes the caller's watch record for the given found item. Idempotent: succeeds even if no
     * matching watch record exists.
     *
     * @param foundItemId the found item id to unwatch (path variable).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with {@code {success:true}}.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
    @DeleteMapping("/{foundItemId}")
    public Map<String, Object> unwatch(
            @PathVariable String foundItemId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        watchedItems.deleteByUserIdAndFoundItemId(user.getEmail(), foundItemId); // scoped to this user
        return Map.of("success", true);
    }
}
