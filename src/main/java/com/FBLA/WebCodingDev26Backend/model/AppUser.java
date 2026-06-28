package com.FBLA.WebCodingDev26Backend.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * AppUser is the domain account record for a person using the lost-and-found system
 * (students, staff, admins). It is a Spring Data MongoDB document persisted in the
 * "users" collection (mapped by @Document) rather than a relational JPA entity.
 *
 * Each user is referenced from other models indirectly by identity (e.g. email is used
 * as the actor in CustodyEvent.actorEmail / AuditLog.performedBy, and as contact info
 * on Claim / LostReport). It also links out to an external Appwrite auth account via
 * {@link #appwriteUserId}.
 */
@Document(collection = "users")
public class AppUser {
    // MongoDB document primary key (@Id). Stored as the Mongo _id; null until first save.
    @Id
    private String id;
    // User's display name (first + last).
    private String fullName;
    // Login / contact email. @Indexed(unique = true) enforces a unique index in Mongo so
    // no two user documents can share the same email address.
    @Indexed(unique = true)
    private String email;
    // Authorization role string. Expected values: "student", "staff", "admin".
    // May be null/blank in storage; the getter defaults it to "student" (see getRole()).
    private String role;
    // Identifier of this user's account in the external Appwrite authentication service.
    private String appwriteUserId;
    // URL of the user's profile picture/avatar image.
    private String avatarUrl;
    // User's phone number (used for SMS notifications when opted in).
    private String phoneNumber;
    // Whether email notifications are enabled for this user. Defaults to true.
    private Boolean emailNotificationsEnabled = true;
    // Whether the user has consented (opted in) to receive SMS messages. Defaults to false.
    private Boolean smsOptIn = false;
    // Whether SMS notification delivery is enabled for this user. Defaults to false.
    private Boolean smsNotificationsEnabled = false;
    // Whether outbound webhook notifications are enabled for this user. Defaults to true.
    private Boolean webhookNotificationsEnabled = true;
    // Categories of notifications the user wants to receive (free-form tags). Defaults to empty list.
    private List<String> notificationCategories = new ArrayList<>();
    // Timestamp (ISO-8601 string) when this user record was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this user record was last updated.
    private String updatedDate;

    // --- standard getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    // Non-trivial getter: defaults a missing/blank role to "student" so callers always get a usable role.
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
