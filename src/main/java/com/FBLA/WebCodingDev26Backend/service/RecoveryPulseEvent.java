package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;

public record RecoveryPulseEvent(
        String eventType,
        String category,
        String recipientEmail,
        String relatedItemId,
        String link,
        boolean webhookEnabled,
        Map<String, Object> context
) {
    public RecoveryPulseEvent {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
