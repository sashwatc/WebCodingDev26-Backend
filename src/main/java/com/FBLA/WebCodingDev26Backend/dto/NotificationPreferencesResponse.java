package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

/**
 * Response payload (direction: server -> client) returning a user's current notification
 * preferences — their contact details, enabled channels, and subscribed categories.
 */
public record NotificationPreferencesResponse(
        // The user's email address (notification destination / account identifier).
        String email,
        // The user's phone number used for SMS notifications.
        String phoneNumber,
        // Whether email notifications are currently enabled.
        Boolean emailNotificationsEnabled,
        // Whether the user has opted in to receive SMS messages (consent flag).
        Boolean smsOptIn,
        // Whether SMS notifications are currently enabled.
        Boolean smsNotificationsEnabled,
        // Whether outbound webhook notifications are currently enabled.
        Boolean webhookNotificationsEnabled,
        // Notification categories the user is currently subscribed to.
        List<String> notificationCategories
) {
}
