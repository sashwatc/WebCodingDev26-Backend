package com.FBLA.WebCodingDev26Backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * CaseMessage is a single chat/correspondence message exchanged about a claim (the
 * conversation between a claimant and reviewing staff). Spring Data MongoDB document stored
 * in the "case_messages" collection. JSON property names use snake_case via @JsonProperty.
 *
 * Each message belongs to a {@link Claim} through {@link #claimId}.
 */
@Document(collection = "case_messages")
public class CaseMessage {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Id of the Claim this message belongs to. @Indexed for fast lookup of a claim's thread;
    // serialized as "claim_id".
    @Indexed
    @JsonProperty("claim_id")
    private String claimId;
    // Id of the user who sent the message; serialized as "sender_id".
    @JsonProperty("sender_id")
    private String senderId;
    // Role of the sender (e.g. "student"/"staff"/"admin"); serialized as "sender_role".
    @JsonProperty("sender_role")
    private String senderRole;
    // The message body text.
    private String message;
    // Timestamp (ISO-8601 string) the message was sent; serialized as "created_at".
    @JsonProperty("created_at")
    private String createdAt;
    // Whether the recipient has read the message. Defaults to false; serialized as "is_read".
    @JsonProperty("is_read")
    private Boolean isRead = false;
    // Timestamp (ISO-8601 string) the message was read; serialized as "read_at".
    @JsonProperty("read_at")
    private String readAt;

    // --- standard getters/setters ---
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
