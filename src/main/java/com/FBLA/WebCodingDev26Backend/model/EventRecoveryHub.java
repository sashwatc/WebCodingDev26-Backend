package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * EventRecoveryHub represents a time-bounded event (e.g. a game, dance, or assembly) that acts
 * as a dedicated lost-and-found context, grouping the campus zones and items associated with it.
 * Spring Data MongoDB document stored in the "event_recovery_hubs" collection.
 *
 * Groups {@link CampusZone}s via {@link #campusZoneIds}; {@link FoundItem} and {@link LostReport}
 * link back to a hub via their eventHubId field.
 */
@Document(collection = "event_recovery_hubs")
public class EventRecoveryHub {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Multi-tenancy owner id (which organization/school this hub belongs to).
    private String tenantId;
    // Display name of the event hub.
    private String name;
    // Longer description of the event.
    private String description;
    // Category of event (e.g. "sports", "dance", "assembly").
    private String eventType;
    // Event start time (ISO-8601 string).
    private String startTime;
    // Event end time (ISO-8601 string).
    private String endTime;
    // Lifecycle status of the hub (e.g. active/closed).
    private String status;
    // Ids of CampusZones included in this event hub. Defaults to empty list.
    private List<String> campusZoneIds = new ArrayList<>();
    // Whether the hub is exposed on the public-facing portal. Nullable.
    private Boolean publicEnabled;
    // Whether the hub is shown on display/kiosk screens. Nullable.
    private Boolean displayEnabled;
    // Identity (email) of the user who created the hub.
    private String createdBy;
    // Timestamp (ISO-8601 string) when this hub was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this hub was last updated.
    private String updatedDate;

    // --- standard getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getCampusZoneIds() { return campusZoneIds; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setCampusZoneIds(List<String> campusZoneIds) { this.campusZoneIds = campusZoneIds == null ? new ArrayList<>() : campusZoneIds; }
    public Boolean getPublicEnabled() { return publicEnabled; }
    public void setPublicEnabled(Boolean publicEnabled) { this.publicEnabled = publicEnabled; }
    public Boolean getDisplayEnabled() { return displayEnabled; }
    public void setDisplayEnabled(Boolean displayEnabled) { this.displayEnabled = displayEnabled; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
