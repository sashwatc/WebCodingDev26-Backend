package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import java.util.List;

public record PublicEventHubResponse(
        String id,
        String tenantId,
        String name,
        String description,
        String eventType,
        String startTime,
        String endTime,
        String status,
        List<String> campusZoneIds,
        Boolean publicEnabled,
        Boolean displayEnabled,
        String createdDate,
        String updatedDate
) {
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
