package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import java.util.List;

/**
 * Public-facing projection (direction: server -> client) of an {@link EventRecoveryHub},
 * exposing only the fields safe to show on public event/display pages. Built from the
 * internal entity via {@link #from(EventRecoveryHub)}.
 */
public record PublicEventHubResponse(
        // Unique identifier of the event recovery hub.
        String id,
        // Tenant/organization the hub belongs to (multi-tenancy key).
        String tenantId,
        // Display name of the event/hub.
        String name,
        // Description of the event/hub.
        String description,
        // Type/category of the event (e.g. game, concert, conference).
        String eventType,
        // Scheduled start time of the event (timestamp string).
        String startTime,
        // Scheduled end time of the event (timestamp string).
        String endTime,
        // Current lifecycle status of the hub (e.g. active/closed).
        String status,
        // Identifiers of the campus zones associated with this hub.
        List<String> campusZoneIds,
        // Whether the public-facing hub page is enabled.
        Boolean publicEnabled,
        // Whether the kiosk/display feed is enabled for this hub.
        Boolean displayEnabled,
        // When the hub record was created (timestamp string).
        String createdDate,
        // When the hub record was last updated (timestamp string).
        String updatedDate
) {
    /**
     * Factory that maps an internal {@link EventRecoveryHub} entity to this public DTO,
     * copying each exposed field straight from the entity's getters.
     */
    public static PublicEventHubResponse from(EventRecoveryHub hub) {
        return new PublicEventHubResponse(
                hub.getId(),
                hub.getTenantId(),
                hub.getName(),
                hub.getDescription(),
                hub.getEventType(),
                hub.getStartTime(),
                hub.getEndTime(),
                hub.getStatus(),
                hub.getCampusZoneIds(),
                hub.getPublicEnabled(),
                hub.getDisplayEnabled(),
                hub.getCreatedDate(),
                hub.getUpdatedDate()
        );
    }
}
