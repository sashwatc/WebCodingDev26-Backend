package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import java.util.List;

/**
 * RESPONSE DTO: the public-facing view of a single found item.
 *
 * <p>Direction: server -> client (outbound response only).</p>
 *
 * <p>Returned by the public/unauthenticated found-item endpoints (e.g. the
 * public item listing and item-detail lookups). It exposes a deliberately
 * curated subset of the internal {@link FoundItem} model: only fields that are
 * safe to show to anyone browsing lost-and-found items, omitting sensitive
 * internal/administrative data that lives on the full entity.</p>
 */
public record PublicFoundItemResponse(
        // Unique identifier of the found item (database/entity ID).
        String id,
        // Short human-readable title/name of the item.
        String title,
        // Longer free-text description of the item.
        String description,
        // Top-level category (e.g. "Electronics", "Clothing").
        String category,
        // More specific subcategory within the category.
        String subcategory,
        // Primary color of the item.
        String color,
        // Brand/manufacturer of the item, if known.
        String brand,
        // Free-text location where the item was found.
        String locationFound,
        // Date the item was found (string as stored, e.g. ISO "yyyy-MM-dd").
        String dateFound,
        // Time of day the item was found (string as stored).
        String timeFound,
        // Lifecycle status of the item (e.g. active/claimed/archived).
        String status,
        // Record type discriminator distinguishing kinds of records.
        String recordType,
        // Timestamp the record was created (string as stored).
        String createdDate,
        // Timestamp the record was last updated (string as stored).
        String updatedDate,
        // Physical condition of the item (e.g. new/used/damaged).
        String condition,
        // Handling priority of the item.
        String priority,
        // ID of the associated event hub, if the item is tied to an event.
        String eventHubId,
        // ID of the associated campus zone, if the item is tied to a zone.
        String campusZoneId,
        // URLs of uploaded photos of the item (may be empty).
        List<String> photoUrls,
        // Free-form descriptive tags for searching/filtering (may be empty).
        List<String> tags
) {
    /**
     * Factory mapper: builds the public response from a full {@link FoundItem}
     * entity by copying through only the public-safe getters. Field order here
     * must match the record component order above.
     */
    public static PublicFoundItemResponse from(FoundItem item) {
        return new PublicFoundItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getSubcategory(),
                item.getColor(),
                item.getBrand(),
                item.getLocationFound(),
                item.getDateFound(),
                item.getTimeFound(),
                item.getStatus(),
                item.getRecordType(),
                item.getCreatedDate(),
                item.getUpdatedDate(),
                item.getCondition(),
                item.getPriority(),
                item.getEventHubId(),
                item.getCampusZoneId(),
                item.getPhotoUrls(),
                item.getTags()
        );
    }
}
