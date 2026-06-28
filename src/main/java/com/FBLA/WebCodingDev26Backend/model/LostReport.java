package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * LostReport is a report filed by someone who has lost an item, describing the item and how to
 * reach the owner so the system can match it against {@link FoundItem}s. Spring Data MongoDB
 * document stored in the "lost_reports" collection.
 * @JsonIgnoreProperties(ignoreUnknown = true) makes deserialization tolerant of extra JSON fields.
 *
 * May be linked to a {@link FoundItem} (which carries linkedLostReportId), and optionally to an
 * {@link EventRecoveryHub} (eventHubId) and {@link CampusZone} (campusZoneId).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "lost_reports")
public class LostReport {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Short title/name of the lost item (also exposed via the itemType alias accessor).
    private String title;
    // Category of the lost item.
    private String category;
    // Full description of the lost item.
    private String description;
    // Color of the lost item.
    private String color;
    // Brand/manufacturer of the lost item.
    private String brand;
    // Where the item was lost / last seen (also exposed via the lastSeenLocation alias accessor).
    private String locationLost;
    // Date the item was lost (string form).
    private String dateLost;
    // Time the item was lost (string form).
    private String timeLost;
    // Name of the person to contact (the owner/reporter).
    private String contactName;
    // Contact email of the reporter.
    private String contactEmail;
    // Contact phone of the reporter.
    private String contactPhone;
    // Workflow status of the report (e.g. open/matched/resolved).
    private String status;
    // How urgent recovery is (e.g. "low"/"medium"/"high").
    private String urgency;
    // Timestamp (ISO-8601 string) when this report was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this report was last updated.
    private String updatedDate;
    // Any additional free-text notes from the reporter.
    private String extraNotes;
    // Reporter's student id, if applicable.
    private String studentId;
    // Id of the EventRecoveryHub this report is associated with, if any.
    private String eventHubId;
    // Id of the CampusZone where the item was lost, if any.
    private String campusZoneId;
    // Flag marking this as seeded demo data (true) vs. a real report. Nullable.
    private Boolean isDemo;

    // URLs of photos of the lost item. Defaults to empty list.
    private List<String> photoUrls = new ArrayList<>();

    // Candidate found items matched to this report (loosely typed). Defaults to empty list.
    private List<Object> matchedItems = new ArrayList<>();

    // --- standard getters/setters (aliases and null-guards noted inline) ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    // Alias accessors: "itemType" reads/writes the same underlying title field (JSON compatibility).
    public String getItemType() { return title; }
    public void setItemType(String itemType) { this.title = itemType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getLocationLost() { return locationLost; }
    public void setLocationLost(String locationLost) { this.locationLost = locationLost; }
    public String getDateLost() { return dateLost; }
    public void setDateLost(String dateLost) { this.dateLost = dateLost; }
    // Alias accessors: "lastSeenLocation" reads/writes the same underlying locationLost field.
    public String getLastSeenLocation() { return locationLost; }
    public void setLastSeenLocation(String lastSeenLocation) { this.locationLost = lastSeenLocation; }
    public String getTimeLost() { return timeLost; }
    public void setTimeLost(String timeLost) { this.timeLost = timeLost; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public String getExtraNotes() { return extraNotes; }
    public void setExtraNotes(String extraNotes) { this.extraNotes = extraNotes; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getEventHubId() { return eventHubId; }
    public void setEventHubId(String eventHubId) { this.eventHubId = eventHubId; }
    public String getCampusZoneId() { return campusZoneId; }
    public void setCampusZoneId(String campusZoneId) { this.campusZoneId = campusZoneId; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
    public List<String> getPhotoUrls() { return photoUrls; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
    // Convenience getter: returns the first photo URL (or "" if none) for single-image consumers.
    public String getPhotoUrl() { return photoUrls == null || photoUrls.isEmpty() ? "" : photoUrls.get(0); }
    // Convenience setter: replaces photoUrls with a single-element list (or empty if blank/null).
    public void setPhotoUrl(String photoUrl) {
        this.photoUrls = photoUrl == null || photoUrl.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(photoUrl));
    }
    public List<Object> getMatchedItems() { return matchedItems; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setMatchedItems(List<Object> matchedItems) { this.matchedItems = matchedItems == null ? new ArrayList<>() : matchedItems; }
}
