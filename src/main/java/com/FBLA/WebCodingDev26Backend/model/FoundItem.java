package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * FoundItem is the core record for a physical item that has been turned in / found and is held
 * in the lost-and-found system. It captures the item's descriptive attributes, where/when it was
 * found, who found it, storage/handling metadata, verification clues, and lifecycle status.
 * Spring Data MongoDB document stored in the "found_items" collection.
 *
 * Relationships: claimed by {@link Claim} (via foundItemId), tracked by {@link CustodyEvent},
 * optionally tied to an {@link EventRecoveryHub} (eventHubId), a {@link CampusZone} (campusZoneId),
 * an {@link AssetRegistryRecord} (assetTag/assetRecordId), and a matched {@link LostReport}
 * (linkedLostReportId). The status values follow {@link ItemStatus}.
 */
@Document(collection = "found_items")
public class FoundItem {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Short title/name of the item.
    private String title;
    // Full description of the item.
    private String description;
    // Top-level category (e.g. "Electronics", "Clothing").
    private String category;
    // More specific sub-category within the category.
    private String subcategory;
    // Primary color of the item.
    private String color;
    // Brand/manufacturer of the item.
    private String brand;
    // Where the item was found.
    private String locationFound;
    // Date the item was found (string form).
    private String dateFound;
    // Time the item was found (string form).
    private String timeFound;
    // Lifecycle status; canonical values defined in ItemStatus (FOUND, CLAIM_PENDING, VERIFIED, ARCHIVED).
    private String status;
    // Discriminator for the kind of record (e.g. distinguishing found-item record variants).
    private String recordType;
    // Timestamp (ISO-8601 string) when this item record was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this item record was last updated.
    private String updatedDate;
    // AI-generated description of the item (from image/text analysis).
    private String aiDescription;
    // Notable distinguishing features used to verify ownership.
    private String distinguishingFeatures;
    // Name of the person who found/turned in the item.
    private String finderName;
    // Email of the finder.
    private String finderEmail;
    // Role of the finder (e.g. "student"/"staff").
    private String finderRole;
    // Where the item is physically stored while held.
    private String storageLocation;
    // Condition of the item (e.g. "good", "damaged").
    private String condition;
    // Handling priority (e.g. "low"/"medium"/"high").
    private String priority;
    // Short human-friendly code/identifier for the item.
    private String itemCode;
    // Identity (email) of the staff member the item is assigned to.
    private String assignedTo;
    // Whether the item is flagged for attention (e.g. high-value/suspicious). Nullable.
    private Boolean isFlagged;
    // Whether a claim on this item has been confirmed/returned. Nullable.
    private Boolean claimConfirmed;
    // Timestamp (ISO-8601 string) when the claim was confirmed.
    private String claimConfirmedAt;
    // Private clues (hidden from public) used to challenge/verify claimants. Defaults to empty list.
    private List<String> privateVerificationClues = new ArrayList<>();
    // Whether the item has restricted visibility (e.g. hidden from public listings). Nullable.
    private Boolean restrictedVisibility;
    // Asset tag if this item matches a registered institutional asset.
    private String assetTag;
    // Id of the linked AssetRegistryRecord, if any.
    private String assetRecordId;
    // Department the item should be routed/returned to.
    private String departmentDestination;
    // Id of the EventRecoveryHub this item is associated with, if any.
    private String eventHubId;
    // Id of the CampusZone where the item was found, if any.
    private String campusZoneId;
    // Id of a LostReport this found item has been matched to, if any.
    private String linkedLostReportId;
    // Flag marking this as seeded demo data (true) vs. a real item. Nullable.
    private Boolean isDemo;

    // URLs of photos of the item. Defaults to empty list.
    private List<String> photoUrls = new ArrayList<>();

    // Free-form search/classification tags. Defaults to empty list.
    private List<String> tags = new ArrayList<>();

    // Embedded list of Rating value objects left on this item. Defaults to empty list.
    private List<Rating> ratings = new ArrayList<>();

    // --- standard getters/setters (null-guarded collection setters noted inline) ---
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
    // Null-guarded setter: substitutes an empty list when given null.
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
    // Null-guarded setter: substitutes an empty list when given null.
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
    public List<String> getTags() { return tags; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : tags; }
    public List<Rating> getRatings() { return ratings; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setRatings(List<Rating> ratings) { this.ratings = ratings == null ? new ArrayList<>() : ratings; }
}
