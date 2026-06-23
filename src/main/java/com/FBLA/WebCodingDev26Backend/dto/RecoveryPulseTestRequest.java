package com.FBLA.WebCodingDev26Backend.dto;

public record RecoveryPulseTestRequest(
        Boolean simulateWebhook,
        String eventType
) {
}
