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

/**
 * Public read endpoints powering the EventHub / Display pages.
 *
 * <p>An "event recovery hub" groups a temporary event (e.g. a game or assembly) with the
 * campus zones it covers and the found items reported under it, so a public display screen
 * can show what has been turned in. All endpoints here are unauthenticated, read-only, and
 * expose only public DTO projections (no PII).
 *
 * <p>Base route: {@code /api/event-hubs} (set by {@link RequestMapping}). {@link RestController}
 * marks this as a REST controller whose method return values are serialized straight to the
 * HTTP response body (typically JSON).
 *
 * <p>Collaborators: reads hubs/zones/items from their repositories and uses
 * {@link FoundItemService} to decide which found items are safe to show publicly.
 */
@RestController
@RequestMapping("/api/event-hubs")
public class EventHubController {
    // Persistence access for event recovery hubs (lookup hub metadata + its zone ids).
    private final EventRecoveryHubRepository hubs;
    // Persistence access for campus zones; used to resolve a hub's zone ids into full zone records.
    private final CampusZoneRepository campusZones;
    // Persistence access for found items; used to list items reported under a given hub.
    private final FoundItemRepository foundItems;
    // Domain service that encapsulates the rule for whether a found item may be shown publicly.
    private final FoundItemService foundItemService;

    /**
     * Constructor injection of the repositories and service this controller depends on.
     * Spring supplies each bean automatically at startup.
     */
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

    /**
     * GET {@code /api/event-hubs/{id}} — fetch a single event hub as a public projection.
     *
     * @param id path variable: the event hub's id.
     * @return 200 OK with a {@link PublicEventHubResponse} when the hub exists; 404 Not Found
     *         (empty body) when no hub matches the id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PublicEventHubResponse> get(@PathVariable String id) {
        // Look up the hub, map it to the public DTO and wrap in 200 OK; if absent, return 404.
        return hubs.findById(id)
                .map(PublicEventHubResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET {@code /api/event-hubs/{id}/display-feed} — the aggregated feed a public display screen
     * renders for one hub: the hub itself, its campus zones, and its publicly visible found items.
     *
     * <p>Always returns 200 OK with a {@link DisplayFeedResponse} (never 404). When the hub does
     * not exist, the response carries a null hub, empty zone/item lists, and an explanatory error
     * message so the front-end can show a graceful "not found" state instead of failing.
     *
     * @param id path variable: the event hub's id.
     * @return 200 OK with the assembled display feed.
     */
    @GetMapping("/{id}/display-feed")
    public DisplayFeedResponse displayFeed(@PathVariable String id) {
        // Resolve the hub (null if it does not exist — handled gracefully below).
        EventRecoveryHub hub = hubs.findById(id).orElse(null);
        List<CampusZone> zones = new ArrayList<>();
        if (hub != null) {
            // Expand each zone id referenced by the hub into its full CampusZone record, skipping
            // any id that no longer resolves to a zone.
            for (String zoneId : hub.getCampusZoneIds()) {
                campusZones.findById(zoneId).ifPresent(zones::add);
            }
        }
        // Load found items for this hub, keep only those the service deems publicly visible,
        // and project each into the public (PII-free) DTO.
        List<PublicFoundItemResponse> items = foundItems.findByEventHubId(id).stream()
                .filter(foundItemService::isPubliclyVisible)
                .map(PublicFoundItemResponse::from)
                .toList();
        // Build the feed; when the hub is missing, send null hub + a user-facing error string.
        return new DisplayFeedResponse(
                hub == null ? null : PublicEventHubResponse.from(hub),
                zones,
                items,
                hub == null ? "Event hub not found." : null
        );
    }
}
