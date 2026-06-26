package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "case_messages")
public class CaseMessage {
    @Id
    private String id;
    @Indexed
    @JsonProperty("claim_id")
    private String claimId;
    @JsonProperty("sender_id")
    private String senderId;
    @JsonProperty("sender_role")
    private String senderRole;
    private String message;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("is_read")
    private Boolean isRead = false;
    @JsonProperty("read_at")
    private String readAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public String getReadAt() { return readAt; }
    public void setReadAt(String readAt) { this.readAt = readAt; }
}
