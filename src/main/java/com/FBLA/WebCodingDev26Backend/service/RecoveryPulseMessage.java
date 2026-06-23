package com.FBLA.WebCodingDev26Backend.service;

public record RecoveryPulseMessage(
        String title,
        String inAppMessage,
        String emailSubject,
        String emailBody,
        String smsBody,
        String webhookSummary,
        String safePreview
) {
}
