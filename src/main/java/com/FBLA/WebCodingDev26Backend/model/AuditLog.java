package com.FBLA.WebCodingDev26Backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class AuditLog {
    @Id
    private String id;
    private String action;
    private String entityType;
    private String entityId;
    private String performedBy;
    @Lob
    private String details;
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
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
}
