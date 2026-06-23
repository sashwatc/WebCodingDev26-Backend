package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notification_deliveries")
public class NotificationDelivery {
    @Id
    private String id;
    private String notificationId;
    private String recipientUserEmail;
    private String recipientEmail;
    private String recipientPhoneMasked;
    private String channel;
    private String eventType;
    private String deliveryStatus;
    private String provider;
    private String providerMessageId;
    private String errorMessage;
    private String createdDate;
    private String sentDate;
    private String safeMessagePreview;
    private Boolean isDemo;

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
