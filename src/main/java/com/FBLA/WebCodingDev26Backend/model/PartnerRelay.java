package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "partner_relays")
public class PartnerRelay {
    @Id
    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private String recoveryCaseId;
    private String foundItemId;
    private String status;
    private String publicSummary;
    private List<String> redactedMatchReasons = new ArrayList<>();
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
    public String getRecoveryCaseId() { return recoveryCaseId; }
    public void setRecoveryCaseId(String recoveryCaseId) { this.recoveryCaseId = recoveryCaseId; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublicSummary() { return publicSummary; }
    public void setPublicSummary(String publicSummary) { this.publicSummary = publicSummary; }
    public List<String> getRedactedMatchReasons() { return redactedMatchReasons; }
    public void setRedactedMatchReasons(List<String> redactedMatchReasons) { this.redactedMatchReasons = redactedMatchReasons == null ? new ArrayList<>() : redactedMatchReasons; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
