package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * AuditLog is an immutable trail entry recording an action performed against some entity
 * in the system (who did what, to which record, when). Spring Data MongoDB document stored
 * in the "audit_logs" collection.
 *
 * It references other models loosely via {@link #entityType} + {@link #entityId} (e.g.
 * entityType "Claim" with the claim's id) and via {@link #performedBy} (typically a user email).
 */
@Document(collection = "audit_logs")
public class AuditLog {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // The action that was performed (e.g. "create", "approve", "delete").
    private String action;
    // The type/name of the entity the action targeted (e.g. "Claim", "FoundItem").
    private String entityType;
    // The id of the specific entity instance the action targeted.
    private String entityId;
    // Identity of the actor who performed the action (usually a user email; may be "System").
    private String performedBy;
    // Free-form additional details/context about the action.
    private String details;
    // Pre-formatted human-friendly description of the event. Serialized to JSON as
    // "human_readable_message" (@JsonProperty). May be absent on legacy records (see getter).
    @JsonProperty("human_readable_message")
    private String humanReadableMessage;
    // Timestamp (ISO-8601 string) when this audit entry was created.
    private String createdDate;

    // --- standard getters/setters ---
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
    // Non-trivial getter: returns the stored message if present, otherwise builds a readable
    // fallback sentence from action/entityType/entityId/performedBy for legacy records.
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
