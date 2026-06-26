package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.DisplayFeedResponse;
import com.FBLA.WebCodingDev26Backend.dto.PublicEventHubResponse;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read endpoints powering the EventHub / Display pages. */
@RestController
@RequestMapping("/api/event-hubs")
public class EventHubController {
    private final EventRecoveryHubRepository hubs;
    private final CampusZoneRepository campusZones;
    private final FoundItemRepository foundItems;
    private final FoundItemService foundItemService;

    public EventHubController(
            EventRecoveryHubRepository hubs,
            CampusZoneRepository campusZones,
            FoundItemRepository foundItems,
            FoundItemService foundItemService
    ) {
        this.hubs = hubs;
        this.campusZones = campusZones;
        this.foundItems = foundItems;
        this.foundItemService = foundItemService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicEventHubResponse> get(@PathVariable String id) {
        return hubs.findById(id)
                .map(PublicEventHubResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/display-feed")
    public DisplayFeedResponse displayFeed(@PathVariable String id) {
        EventRecoveryHub hub = hubs.findById(id).orElse(null);
        List<CampusZone> zones = new ArrayList<>();
        if (hub != null) {
            for (String zoneId : hub.getCampusZoneIds()) {
                campusZones.findById(zoneId).ifPresent(zones::add);
            }
        }
        List<PublicFoundItemResponse> items = foundItems.findByEventHubId(id).stream()
                .filter(foundItemService::isPubliclyVisible)
                .map(PublicFoundItemResponse::from)
                .toList();
        return new DisplayFeedResponse(
                hub == null ? null : PublicEventHubResponse.from(hub),
                zones,
                items,
                hub == null ? "Event hub not found." : null
        );
    }
}
