package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "recovery_cases")
public class RecoveryCase {
    @Id
    private String id;
    @Indexed(unique = true)
    private String lostReportId;
    private String caseCode;
    private String tenantId;
    private String selectedFoundItemId;
    private String linkedClaimId;
    private String eventHubId;
    private String campusZoneId;
    private String status;
    private String priority;
    private String assignedTo;
    private String summary;
    private String recoveryPlan;
    private List<String> likelyZoneSummaries = new ArrayList<>();
    private String createdDate;
    private String updatedDate;
    private Boolean isDemo;

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
    public List<String> getLikelyZoneSummaries() { return likelyZoneSummaries; }
    public void setLikelyZoneSummaries(List<String> likelyZoneSummaries) { this.likelyZoneSummaries = likelyZoneSummaries == null ? new ArrayList<>() : likelyZoneSummaries; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean isDemo) { this.isDemo = isDemo; }
}
