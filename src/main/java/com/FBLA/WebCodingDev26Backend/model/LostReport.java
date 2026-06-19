package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private List<String> photoUrls = new ArrayList<>();

    private List<String> matchedItems = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
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
    public List<String> getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
    public List<String> getMatchedItems() { return matchedItems; }
    public void setMatchedItems(List<String> matchedItems) { this.matchedItems = matchedItems == null ? new ArrayList<>() : matchedItems; }
}
