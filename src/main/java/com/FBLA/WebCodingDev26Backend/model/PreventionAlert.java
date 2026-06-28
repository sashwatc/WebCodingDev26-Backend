package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A proactive "prevention" alert raised when lost-item activity in a campus zone/category
 * spikes above an expected baseline, prompting staff to take preventive action.
 *
 * <p>Persisted as a MongoDB document in the "prevention_alerts" collection (mapped via
 * {@code @Document}). The alert compares an observed count during a recent time window
 * against a historical baseline count over a baseline window, and bundles the lost reports,
 * reasons and suggested actions that explain it.</p>
 *
 * <p>Related models: scoped to a tenant via {@code tenantId} and a location via
 * {@code campusZoneId}; references source lost reports by id in {@code sourceLostReportIds}.</p>
 */
@Document(collection = "prevention_alerts")
public class PreventionAlert {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Tenant/organization this alert belongs to (multi-tenant scoping). */
    private String tenantId;
    /** Human-readable headline summarizing the alert. */
    private String title;
    /** Kind of alert, e.g. a spike type or detection rule that produced it. */
    private String alertType;
    /** Severity level of the alert, e.g. "low", "medium", "high". */
    private String severity;
    /** Id of the campus zone/location the alert pertains to. */
    private String campusZoneId;
    /** Item category the alert concerns (e.g. electronics, water bottles). */
    private String category;
    /** Start (ISO string) of the recent observation window being evaluated. */
    private String timeWindowStart;
    /** End (ISO string) of the recent observation window being evaluated. */
    private String timeWindowEnd;
    /** Start (ISO string) of the historical baseline window used for comparison. */
    private String baselineWindowStart;
    /** End (ISO string) of the historical baseline window used for comparison. */
    private String baselineWindowEnd;
    /** Expected/historical count of events over the baseline window. */
    private Integer baselineCount;
    /** Actual observed count of events over the recent time window. */
    private Integer observedCount;
    /** Ids of the lost reports that contributed to this alert; defaults to an empty list. */
    private List<String> sourceLostReportIds = new ArrayList<>();
    /** Human-readable reasons explaining why the alert fired; defaults to an empty list. */
    private List<String> reasons = new ArrayList<>();
    /** Recommended preventive actions for staff; defaults to an empty list. */
    private List<String> suggestedActions = new ArrayList<>();
    /** Lifecycle status of the alert, e.g. "active", "resolved". */
    private String status;
    /** Timestamp (ISO string) when the alert's metrics were computed. */
    private String calculatedAt;
    /** Timestamp (ISO string) when the alert document was created. */
    private String createdDate;
    /** Timestamp (ISO string) when the alert was resolved; null while still active. */
    private String resolvedDate;
    /** Identifier (e.g. staff email) of who resolved the alert; null while still active. */
    private String resolvedBy;
    /** Free-text notes describing how/why the alert was resolved. */
    private String resolutionNotes;
    /** Flag marking this as demo/seed data rather than a real production alert. */
    private Boolean isDemo;

    // --- Trivial getters/setters follow; list setters are null-safe (see below). ---
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
    // Null-safe list setters: a null argument is coerced to an empty list so the
    // collection fields are never null for callers/serialization.
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
