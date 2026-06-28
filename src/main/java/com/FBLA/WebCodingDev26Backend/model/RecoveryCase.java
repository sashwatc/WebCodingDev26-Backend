package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An active investigation/recovery case opened to help reunite a user with a lost item.
 *
 * <p>Persisted as a MongoDB document in the "recovery_cases" collection (mapped via
 * {@code @Document}). Each case is created from exactly one lost report and tracks the
 * staff-driven effort (plan, assignee, candidate found item, likely zones) to recover it.</p>
 *
 * <p>Related models: one-to-one with a lost report via the unique-indexed
 * {@code lostReportId}; may link to a candidate found item ({@code selectedFoundItemId})
 * and a claim ({@code linkedClaimId}); scoped to a tenant ({@code tenantId}), event hub
 * ({@code eventHubId}) and campus zone ({@code campusZoneId}).</p>
 */
@Document(collection = "recovery_cases")
public class RecoveryCase {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Id of the originating lost report; uniquely indexed so each report has at most one case. */
    @Indexed(unique = true)
    private String lostReportId;
    /** Short human-friendly case reference code shown to staff/users. */
    private String caseCode;
    /** Tenant/organization this case belongs to (multi-tenant scoping). */
    private String tenantId;
    /** Id of the found item currently selected as the likely match for this case; null if none. */
    private String selectedFoundItemId;
    /** Id of the claim linked to this case once a claim is filed; null otherwise. */
    private String linkedClaimId;
    /** Id of the event hub (e.g. event/venue context) associated with the case. */
    private String eventHubId;
    /** Id of the campus zone/location associated with the case. */
    private String campusZoneId;
    /** Lifecycle status of the case, e.g. "open", "in_progress", "resolved", "closed". */
    private String status;
    /** Priority level of the case, e.g. "low", "medium", "high". */
    private String priority;
    /** Identifier (e.g. staff email) of the person the case is assigned to. */
    private String assignedTo;
    /** Brief summary of the case. */
    private String summary;
    /** The recovery plan / next steps narrative for resolving the case. */
    private String recoveryPlan;
    /** Summaries of zones where the item is likely to be found; defaults to an empty list. */
    private List<String> likelyZoneSummaries = new ArrayList<>();
    /** Timestamp (ISO string) when the case was created. */
    private String createdDate;
    /** Timestamp (ISO string) when the case was last updated. */
    private String updatedDate;
    /** Flag marking this as demo/seed data rather than a real production case. */
    private Boolean isDemo;

    // --- Trivial getters/setters follow; the list setter is null-safe (see below). ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLostReportId() { return lostReportId; }
    public void setLostReportId(String lostReportId) { this.lostReportId = lostReportId; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSelectedFoundItemId() { return selectedFoundItemId; }
    public void setSelectedFoundItemId(String selectedFoundItemId) { this.selectedFoundItemId = selectedFoundItemId; }
    public String getLinkedClaimId() { return linkedClaimId; }
    public void setLinkedClaimId(String linkedClaimId) { this.linkedClaimId = linkedClaimId; }
    public String getEventHubId() { return eventHubId; }
    public void setEventHubId(String eventHubId) { this.eventHubId = eventHubId; }
    public String getCampusZoneId() { return campusZoneId; }
    public void setCampusZoneId(String campusZoneId) { this.campusZoneId = campusZoneId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRecoveryPlan() { return recoveryPlan; }
    public void setRecoveryPlan(String recoveryPlan) { this.recoveryPlan = recoveryPlan; }
    // Null-safe list setter: a null argument is coerced to an empty list so the field is never null.
    public List<String> getLikelyZoneSummaries() { return likelyZoneSummaries; }
    public void setLikelyZoneSummaries(List<String> likelyZoneSummaries) { this.likelyZoneSummaries = likelyZoneSummaries == null ? new ArrayList<>() : likelyZoneSummaries; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
}
