package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "custody_events")
@CompoundIndex(name = "found_item_sequence_unique", def = "{'foundItemId': 1, 'sequenceNumber': 1}", unique = true)
public class CustodyEvent {
    @Id
    private String id;
    @Indexed
    private String foundItemId;
    private Integer sequenceNumber;
    private String eventType;
    private String actorEmail;
    private String actorRole;
    private String location;
    private String notes;
    private String photoEvidenceUrl;
    private String previousEventHash;
    private String eventHash;
    private String createdDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPhotoEvidenceUrl() { return photoEvidenceUrl; }
    public void setPhotoEvidenceUrl(String photoEvidenceUrl) { this.photoEvidenceUrl = photoEvidenceUrl; }
    public String getPreviousEventHash() { return previousEventHash; }
    public void setPreviousEventHash(String previousEventHash) { this.previousEventHash = previousEventHash; }
    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
}
