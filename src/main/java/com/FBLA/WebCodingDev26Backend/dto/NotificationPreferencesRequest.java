package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

/**
 * Request payload (direction: client -> server) used to update a user's notification
 * preferences — which channels they receive notifications on and which categories they
 * subscribe to. Fields are nullable so callers can send partial updates.
 */
public record NotificationPreferencesRequest(
        // Phone number to use for SMS notifications.
        String phoneNumber,
        // Whether email notifications are enabled.
        Boolean emailNotificationsEnabled,
        // Whether the user has opted in to receive SMS messages (consent flag).
        Boolean smsOptIn,
        // Whether SMS notifications are enabled.
        Boolean smsNotificationsEnabled,
        // Whether outbound webhook notifications are enabled.
        Boolean webhookNotificationsEnabled,
        // Notification categories the user is subscribed to (e.g. claim updates, alerts).
        List<String> notificationCategories
) {
}
