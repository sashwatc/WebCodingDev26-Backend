package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * MatchSuggestion is a transient/DTO value object (NOT persisted — no @Document/@Id) describing a
 * candidate {@link FoundItem} that may correspond to a lost item, along with a confidence score and
 * the reasons it was suggested. Returned to clients when proposing matches for a {@link LostReport}.
 * @JsonIgnoreProperties(ignoreUnknown = true) makes deserialization tolerant of extra JSON fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchSuggestion {
    // Id of the suggested FoundItem.
    private String foundItemId;
    // Denormalized title of the suggested found item (for display).
    private String foundItemTitle;
    // Category of the suggested item.
    private String category;
    // Color of the suggested item.
    private String color;
    // Brand of the suggested item.
    private String brand;
    // Where the suggested item was found.
    private String locationFound;
    // Date the suggested item was found (string form).
    private String dateFound;
    // Match confidence score (higher = stronger match). Nullable.
    private Integer confidence;
    // Human-readable reasons explaining why this item was suggested. Defaults to empty list.
    private List<String> reasons = new ArrayList<>();
    // Origin of the suggestion (e.g. which matching algorithm/source produced it).
    private String source;
    // Status of the suggestion (e.g. pending/accepted/dismissed).
    private String status;
    // Timestamp (ISO-8601 string) when the suggestion was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when the suggestion was last updated.
    private String updatedDate;
    // Photo URLs of the suggested item. Defaults to empty list.
    private List<String> photoUrls = new ArrayList<>();

    // --- standard getters/setters (null-guarded collection setters noted inline) ---
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getFoundItemTitle() { return foundItemTitle; }
    public void setFoundItemTitle(String foundItemTitle) { this.foundItemTitle = foundItemTitle; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getLocationFound() { return locationFound; }
    public void setLocationFound(String locationFound) { this.locationFound = locationFound; }
    public String getDateFound() { return dateFound; }
    public void setDateFound(String dateFound) { this.dateFound = dateFound; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public List<String> getReasons() { return reasons; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setReasons(List<String> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public List<String> getPhotoUrls() { return photoUrls; }
    // Null-guarded setter: substitutes an empty list when given null.
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
}
