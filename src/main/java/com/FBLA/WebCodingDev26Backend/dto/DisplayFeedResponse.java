package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import java.util.List;

/**
 * Public display feed for an event recovery hub. Serializes (SNAKE_CASE) to
 * {@code event_hub}, {@code zones}, {@code found_items}, {@code notice} — the keys
 * the EventHub/Display pages read.
 */
public record DisplayFeedResponse(
        // The public-facing event recovery hub metadata (name, status, schedule, etc.).
        PublicEventHubResponse eventHub,
        // The campus zones associated with the hub, used to lay out/label the display.
        List<CampusZone> zones,
        // Public found-item cards to render on the feed (sensitive details stripped).
        List<PublicFoundItemResponse> foundItems,
        // Optional banner/notice text shown on the display (e.g. instructions or alerts);
        // may be null when there is nothing to announce.
        String notice
) {
}
