package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "lost_reports")
public class LostReport {
    @Id
    private String id;
    private String title;
    private String category;
    private String description;
    private String color;
    private String brand;
    private String locationLost;
    private String dateLost;
    private String timeLost;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String status;
    private String urgency;
    private String createdDate;
    private String updatedDate;
    private String extraNotes;
    private String studentId;
    private String eventHubId;
    private String campusZoneId;
    private Boolean isDemo;

    private List<String> photoUrls = new ArrayList<>();

    private List<Object> matchedItems = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
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
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
    public String getPhotoUrl() { return photoUrls == null || photoUrls.isEmpty() ? "" : photoUrls.get(0); }
    public void setPhotoUrl(String photoUrl) {
        this.photoUrls = photoUrl == null || photoUrl.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(photoUrl));
    }
    public List<Object> getMatchedItems() { return matchedItems; }
    public void setMatchedItems(List<Object> matchedItems) { this.matchedItems = matchedItems == null ? new ArrayList<>() : matchedItems; }
}
