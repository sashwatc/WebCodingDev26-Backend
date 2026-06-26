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

@RestController
@RequestMapping("/api/watched-items")
public class WatchedItemController {
    private final WatchedItemRepository watchedItems;
    private final FoundItemRepository foundItems;
    private final DemoAuthorizationService authorizationService;

    public WatchedItemController(
            WatchedItemRepository watchedItems,
            FoundItemRepository foundItems,
            DemoAuthorizationService authorizationService) {
        this.watchedItems = watchedItems;
        this.foundItems = foundItems;
        this.authorizationService = authorizationService;
    }

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchedItem watch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        String foundItemId = body.get("foundItemId") != null ? String.valueOf(body.get("foundItemId")).trim()
                : (body.get("found_item_id") != null ? String.valueOf(body.get("found_item_id")).trim() : "");
        if (foundItemId.isBlank()) {
            throw new BadRequestException("foundItemId is required.");
        }
        foundItems.findById(foundItemId).orElseThrow(() -> new NotFoundException("Found item not found"));
        // Idempotent: return existing if already watched
        return watchedItems.findByUserIdAndFoundItemId(user.getEmail(), foundItemId).orElseGet(() -> {
            WatchedItem wi = new WatchedItem();
            wi.setId("wi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            wi.setUserId(user.getEmail());
            wi.setFoundItemId(foundItemId);
            wi.setCreatedAt(Instant.now().toString());
            return watchedItems.save(wi);
        });
    }

    @DeleteMapping("/{foundItemId}")
    public Map<String, Object> unwatch(
            @PathVariable String foundItemId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser user = authorizationService.currentUser(userEmail);
        if (user == null) {
            throw new ForbiddenException("Sign in is required.");
        }
        watchedItems.deleteByUserIdAndFoundItemId(user.getEmail(), foundItemId);
        return Map.of("success", true);
    }
}
