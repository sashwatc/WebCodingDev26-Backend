package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * CustodyEvent is one link in a tamper-evident chain-of-custody log for a found item: each
 * event records a handoff/action and is hash-chained to the previous event (like a mini
 * blockchain) so the custody history cannot be silently altered. Spring Data MongoDB document
 * stored in the "custody_events" collection.
 *
 * The @CompoundIndex "found_item_sequence_unique" enforces that, per foundItemId, each
 * sequenceNumber is unique — preventing duplicate/out-of-order entries in an item's chain.
 * References the item via {@link #foundItemId} (which is also @Indexed individually).
 */
@Document(collection = "custody_events")
@CompoundIndex(name = "found_item_sequence_unique", def = "{'foundItemId': 1, 'sequenceNumber': 1}", unique = true)
public class CustodyEvent {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Id of the FoundItem this custody event belongs to. @Indexed for fast per-item chain lookup.
    @Indexed
    private String foundItemId;
    // Position of this event within the item's custody chain (1, 2, 3, ...); unique per item.
    private Integer sequenceNumber;
    // Type of custody action (e.g. "intake", "transfer", "release"/handoff).
    private String eventType;
    // Email of the actor who performed this custody action.
    private String actorEmail;
    // Role of the actor (e.g. "staff"/"admin") at the time of the action.
    private String actorRole;
    // Physical location where the custody action took place.
    private String location;
    // Free-text notes about this custody event.
    private String notes;
    // URL of photo evidence captured at this custody step.
    private String photoEvidenceUrl;
    // Hash of the previous event in the chain (links this event to its predecessor; null for first).
    private String previousEventHash;
    // Hash of this event's own contents (incorporates previousEventHash to form the chain).
    private String eventHash;
    // Timestamp (ISO-8601 string) when this custody event was recorded.
    private String createdDate;

    // --- standard getters/setters ---
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
