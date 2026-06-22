package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "event_recovery_hubs")
public class EventRecoveryHub {
    @Id
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String eventType;
    private String startTime;
    private String endTime;
    private String status;
    private List<String> campusZoneIds = new ArrayList<>();
    private Boolean publicEnabled;
    private Boolean displayEnabled;
    private String createdBy;
    private String createdDate;
    private String updatedDate;

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
