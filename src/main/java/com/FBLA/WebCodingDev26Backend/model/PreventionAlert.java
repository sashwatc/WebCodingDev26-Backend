package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "prevention_alerts")
public class PreventionAlert {
    @Id
    private String id;
    private String tenantId;
    private String title;
    private String alertType;
    private String severity;
    private String campusZoneId;
    private String category;
    private String timeWindowStart;
    private String timeWindowEnd;
    private String baselineWindowStart;
    private String baselineWindowEnd;
    private Integer baselineCount;
    private Integer observedCount;
    private List<String> sourceLostReportIds = new ArrayList<>();
    private List<String> reasons = new ArrayList<>();
    private List<String> suggestedActions = new ArrayList<>();
    private String status;
    private String calculatedAt;
    private String createdDate;
    private String resolvedDate;
    private String resolvedBy;
    private String resolutionNotes;
    private Boolean isDemo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getCampusZoneId() { return campusZoneId; }
    public void setCampusZoneId(String campusZoneId) { this.campusZoneId = campusZoneId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTimeWindowStart() { return timeWindowStart; }
    public void setTimeWindowStart(String timeWindowStart) { this.timeWindowStart = timeWindowStart; }
    public String getTimeWindowEnd() { return timeWindowEnd; }
    public void setTimeWindowEnd(String timeWindowEnd) { this.timeWindowEnd = timeWindowEnd; }
    public String getBaselineWindowStart() { return baselineWindowStart; }
    public void setBaselineWindowStart(String baselineWindowStart) { this.baselineWindowStart = baselineWindowStart; }
    public String getBaselineWindowEnd() { return baselineWindowEnd; }
    public void setBaselineWindowEnd(String baselineWindowEnd) { this.baselineWindowEnd = baselineWindowEnd; }
    public Integer getBaselineCount() { return baselineCount; }
    public void setBaselineCount(Integer baselineCount) { this.baselineCount = baselineCount; }
    public Integer getObservedCount() { return observedCount; }
    public void setObservedCount(Integer observedCount) { this.observedCount = observedCount; }
    public List<String> getSourceLostReportIds() { return sourceLostReportIds; }
    public void setSourceLostReportIds(List<String> sourceLostReportIds) { this.sourceLostReportIds = sourceLostReportIds == null ? new ArrayList<>() : sourceLostReportIds; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<String> suggestedActions) { this.suggestedActions = suggestedActions == null ? new ArrayList<>() : suggestedActions; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(String calculatedAt) { this.calculatedAt = calculatedAt; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getResolvedDate() { return resolvedDate; }
    public void setResolvedDate(String resolvedDate) { this.resolvedDate = resolvedDate; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
}
