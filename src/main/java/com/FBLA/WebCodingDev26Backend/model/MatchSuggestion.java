package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchSuggestion {
    private String foundItemId;
    private String foundItemTitle;
    private String category;
    private String color;
    private String brand;
    private String locationFound;
    private String dateFound;
    private Integer confidence;
    private List<String> reasons = new ArrayList<>();
    private String source;
    private String status;
    private String createdDate;
    private String updatedDate;
    private List<String> photoUrls = new ArrayList<>();

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
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls == null ? new ArrayList<>() : photoUrls; }
}
