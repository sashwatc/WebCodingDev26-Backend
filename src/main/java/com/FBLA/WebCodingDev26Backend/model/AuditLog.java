package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    private String action;
    private String entityType;
    private String entityId;
    private String performedBy;
    private String details;
    @JsonProperty("human_readable_message")
    private String humanReadableMessage;
    private String createdDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    @JsonProperty("human_readable_message")
    public String getHumanReadableMessage() {
        if (humanReadableMessage != null && !humanReadableMessage.isBlank()) {
            return humanReadableMessage;
        }
        // Generate a readable fallback for records created before this field existed
        String actor = performedBy != null ? performedBy : "System";
        String verb = action != null ? action : "updated";
        String subject = entityType != null ? entityType : "record";
        String ref = entityId != null ? " (" + entityId + ")" : "";
        return actor + " performed action '" + verb + "' on " + subject + ref;
    }
    public void setHumanReadableMessage(String humanReadableMessage) { this.humanReadableMessage = humanReadableMessage; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
}
