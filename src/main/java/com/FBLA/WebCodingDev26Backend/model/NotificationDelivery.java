package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Audit/log record of a single outbound notification delivery attempt over an external
 * channel (e.g. email or SMS).
 *
 * <p>Persisted as a MongoDB document in the "notification_deliveries" collection (mapped
 * via {@code @Document}). Whereas {@link Notification} represents the in-app message,
 * this entity records each attempt to push that message out through a provider, including
 * recipient targeting, provider response and any failure detail.</p>
 *
 * <p>Related models: links back to {@link Notification} via {@code notificationId}.</p>
 */
@Document(collection = "notification_deliveries")
public class NotificationDelivery {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Id of the {@link Notification} this delivery attempt is for. */
    private String notificationId;
    /** Email/login identifier of the user being notified (recipient account). */
    private String recipientUserEmail;
    /** Actual email address the message was sent to (may differ from the account email). */
    private String recipientEmail;
    /** Masked phone number for SMS deliveries (e.g. ***-***-1234) to avoid storing it in clear. */
    private String recipientPhoneMasked;
    /** Delivery channel used, e.g. "email" or "sms". */
    private String channel;
    /** Type of the triggering event (e.g. match_found, claim_approved) for analytics/filtering. */
    private String eventType;
    /** Outcome status of the delivery, e.g. "queued", "sent", "failed". */
    private String deliveryStatus;
    /** Name of the external sending provider (e.g. the email/SMS gateway). */
    private String provider;
    /** Provider-assigned message id used to correlate/track the message with the provider. */
    private String providerMessageId;
    /** Error detail captured when {@code deliveryStatus} indicates failure; null on success. */
    private String errorMessage;
    /** Timestamp (ISO string) when this delivery record was created/queued. */
    private String createdDate;
    /** Timestamp (ISO string) when the message was actually sent; null if not yet sent. */
    private String sentDate;
    /** Redacted/safe excerpt of the message content stored for auditing without sensitive data. */
    private String safeMessagePreview;
    /** Flag marking this as demo/seed data rather than a real production delivery. */
    private Boolean isDemo;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getRecipientUserEmail() { return recipientUserEmail; }
    public void setRecipientUserEmail(String recipientUserEmail) { this.recipientUserEmail = recipientUserEmail; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getRecipientPhoneMasked() { return recipientPhoneMasked; }
    public void setRecipientPhoneMasked(String recipientPhoneMasked) { this.recipientPhoneMasked = recipientPhoneMasked; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getSentDate() { return sentDate; }
    public void setSentDate(String sentDate) { this.sentDate = sentDate; }
    public String getSafeMessagePreview() { return safeMessagePreview; }
    public void setSafeMessagePreview(String safeMessagePreview) { this.safeMessagePreview = safeMessagePreview; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean demo) { isDemo = demo; }
}
