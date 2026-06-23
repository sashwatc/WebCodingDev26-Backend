package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "found_items")
public class FoundItem {
    @Id
    private String id;
    private String title;
    private String description;
    private String category;
    private String subcategory;
    private String color;
    private String brand;
    private String locationFound;
    private String dateFound;
    private String timeFound;
    private String status;
    private String recordType;
    private String createdDate;
    private String updatedDate;
    private String aiDescription;
    private String distinguishingFeatures;
    private String finderName;
    private String finderEmail;
    private String finderRole;
    private String storageLocation;
    private String condition;
    private String priority;
    private String itemCode;
    private String assignedTo;
    private Boolean isFlagged;
    private Boolean claimConfirmed;
    private String claimConfirmedAt;
    private List<String> privateVerificationClues = new ArrayList<>();
    private Boolean restrictedVisibility;
    private String assetTag;
    private String assetRecordId;
    private String departmentDestination;
    private String eventHubId;
    private String campusZoneId;
    private String linkedLostReportId;
    private Boolean isDemo;

    private List<String> photoUrls = new ArrayList<>();

    private List<String> tags = new ArrayList<>();

    private List<Rating> ratings = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getLocationFound() { return locationFound; }
    public void setLocationFound(String locationFound) { this.locationFound = locationFound; }
    public String getDateFound() { return dateFound; }
    public void setDateFound(String dateFound) { this.dateFound = dateFound; }
    public String getTimeFound() { return timeFound; }
    public void setTimeFound(String timeFound) { this.timeFound = timeFound; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public String getDistinguishingFeatures() { return distinguishingFeatures; }
    public void setDistinguishingFeatures(String distinguishingFeatures) { this.distinguishingFeatures = distinguishingFeatures; }
    public String getFinderName() { return finderName; }
    public void setFinderName(String finderName) { this.finderName = finderName; }
    public String getFinderEmail() { return finderEmail; }
    public void setFinderEmail(String finderEmail) { this.finderEmail = finderEmail; }
    public String getFinderRole() { return finderRole; }
    public void setFinderRole(String finderRole) { this.finderRole = finderRole; }
    public String getStorageLocation() { return storageLocation; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Boolean getIsFlagged() { return isFlagged; }
    public void setIsFlagged(Boolean flagged) { isFlagged = flagged; }
    public Boolean getClaimConfirmed() { return claimConfirmed; }
    public void setClaimConfirmed(Boolean claimConfirmed) { this.claimConfirmed = claimConfirmed; }
    public String getClaimConfirmedAt() { return claimConfirmedAt; }
    public void setClaimConfirmedAt(String claimConfirmedAt) { this.claimConfirmedAt = claimConfirmedAt; }
    public List<String> getPrivateVerificationClues() { return privateVerificationClues; }
    public void setPrivateVerificationClues(List<String> privateVerificationClues) { this.privateVerificationClues = privateVerificationClues == null ? new ArrayList<>() : privateVerificationClues; }
    public Boolean getRestrictedVisibility() { return restrictedVisibility; }
    public void setRestrictedVisibility(Boolean restrictedVisibility) { this.restrictedVisibility = restrictedVisibility; }
    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }
    public String getAssetRecordId() { return assetRecordId; }
    public void setAssetRecordId(String assetRecordId) { this.assetRecordId = assetRecordId; }
    public String getDepartmentDestination() { return departmentDestination; }
    public void setDepartmentDestination(String departmentDestination) { this.departmentDestination = departmentDestination; }
    public String getEventHubId() { return eventHubId; }
    public void setEventHubId(String eventHubId) { this.eventHubId = eventHubId; }
    public String getCampusZoneId() { return campusZoneId; }
    public void setCampusZoneId(String campusZoneId) { this.campusZoneId = campusZoneId; }
    public String getLinkedLostReportId() { return linkedLostReportId; }
    public void setLinkedLostReportId(String linkedLostReportId) { this.linkedLostReportId = linkedLostReportId; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
    public List<String> getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : tags; }
    public List<Rating> getRatings() { return ratings; }
    public void setRatings(List<Rating> ratings) { this.ratings = ratings == null ? new ArrayList<>() : ratings; }
}
