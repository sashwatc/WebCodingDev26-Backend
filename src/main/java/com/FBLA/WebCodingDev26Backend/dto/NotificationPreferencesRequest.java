package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record NotificationPreferencesRequest(
        String phoneNumber,
        Boolean emailNotificationsEnabled,
        Boolean smsOptIn,
        Boolean smsNotificationsEnabled,
        Boolean webhookNotificationsEnabled,
        List<String> notificationCategories
) {
}
