package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * In-app notification shown to a single user.
 *
 * <p>Persisted as a MongoDB document in the "notifications" collection (mapped via
 * {@code @Document}, not JPA). Each document is one notification addressed to one user
 * (by {@code userEmail}) and typically points at a related domain object (e.g. a found
 * item) through {@code relatedItemId} / {@code link}.</p>
 *
 * <p>Related models: references items elsewhere in the system by {@code relatedItemId};
 * the actual outbound delivery of a notification (email/SMS) is tracked separately by
 * {@link NotificationDelivery}.</p>
 */
@Document(collection = "notifications")
public class Notification {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Email address of the user this notification is addressed to (the owner/recipient). */
    private String userEmail;
    /** Short headline/title of the notification. */
    private String title;
    /** Full notification body text. */
    private String message;
    /** Category of notification used for grouping/icon selection (e.g. match, claim, system). */
    private String type;
    /** Optional in-app URL the notification links to when clicked. */
    private String link;
    /** Optional id of the domain object (e.g. found item / claim) this notification concerns. */
    private String relatedItemId;
    /** Whether the user has read this notification (true = read, false/null = unread). */
    private Boolean isRead;
    /** Timestamp (ISO string) when the notification was created. */
    private String createdDate;
    /** Timestamp (ISO string) when the notification was last updated (e.g. marked read). */
    private String updatedDate;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getRelatedItemId() { return relatedItemId; }
    public void setRelatedItemId(String relatedItemId) { this.relatedItemId = relatedItemId; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean read) { isRead = read; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
