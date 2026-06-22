package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "recovery_missions")
public class RecoveryMission {
    @Id
    private String id;
    @Indexed
    private String recoveryCaseId;
    private String eventHubId;
    private String campusZoneId;
    private String zoneLabel;
    private String title;
    private String recommendedAction;
    private List<String> reasons = new ArrayList<>();
    private Integer score;
    private String priority;
    private String status;
    private String assignedTo;
    private String dueAt;
    private String completedBy;
    private String completedDate;
    private String notes;
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRecoveryCaseId() { return recoveryCaseId; }
    public void setRecoveryCaseId(String recoveryCaseId) { this.recoveryCaseId = recoveryCaseId; }
    public String getEventHubId() { return eventHubId; }
    public void setEventHubId(String eventHubId) { this.eventHubId = eventHubId; }
    public String getCampusZoneId() { return campusZoneId; }
    public void setCampusZoneId(String campusZoneId) { this.campusZoneId = campusZoneId; }
    public String getZoneLabel() { return zoneLabel; }
    public void setZoneLabel(String zoneLabel) { this.zoneLabel = zoneLabel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getDueAt() { return dueAt; }
    public void setDueAt(String dueAt) { this.dueAt = dueAt; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public String getCompletedDate() { return completedDate; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
