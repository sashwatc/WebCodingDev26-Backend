package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import java.util.List;

/**
 * Public display feed for an event recovery hub. Serializes (SNAKE_CASE) to
 * {@code event_hub}, {@code zones}, {@code found_items}, {@code notice} — the keys
 * the EventHub/Display pages read.
 */
public record DisplayFeedResponse(
        PublicEventHubResponse eventHub,
        List<CampusZone> zones,
        List<PublicFoundItemResponse> foundItems,
        String notice
) {
}
