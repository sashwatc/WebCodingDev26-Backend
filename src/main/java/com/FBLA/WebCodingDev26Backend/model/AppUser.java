package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class AppUser {
    @Id
    private String id;
    private String fullName;
    @Indexed(unique = true)
    private String email;
    private String role;
    private String appwriteUserId;
    private String avatarUrl;
    private String phoneNumber;
    private Boolean emailNotificationsEnabled = true;
    private Boolean smsOptIn = false;
    private Boolean smsNotificationsEnabled = false;
    private Boolean webhookNotificationsEnabled = true;
    private List<String> notificationCategories = new ArrayList<>();
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return (role == null || role.isBlank()) ? "student" : role; }
    public void setRole(String role) { this.role = role; }
    public String getAppwriteUserId() { return appwriteUserId; }
    public void setAppwriteUserId(String appwriteUserId) { this.appwriteUserId = appwriteUserId; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Boolean getEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }
    public Boolean getSmsOptIn() { return smsOptIn; }
    public void setSmsOptIn(Boolean smsOptIn) { this.smsOptIn = smsOptIn; }
    public Boolean getSmsNotificationsEnabled() { return smsNotificationsEnabled; }
    public void setSmsNotificationsEnabled(Boolean smsNotificationsEnabled) { this.smsNotificationsEnabled = smsNotificationsEnabled; }
    public Boolean getWebhookNotificationsEnabled() { return webhookNotificationsEnabled; }
    public void setWebhookNotificationsEnabled(Boolean webhookNotificationsEnabled) { this.webhookNotificationsEnabled = webhookNotificationsEnabled; }
    public List<String> getNotificationCategories() { return notificationCategories; }
    public void setNotificationCategories(List<String> notificationCategories) { this.notificationCategories = notificationCategories; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
