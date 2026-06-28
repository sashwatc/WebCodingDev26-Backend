package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;

/**
 * Immutable description of a single notifiable event handed to the {@link RecoveryPulseDispatcher}.
 * It carries everything the dispatcher and template renderer need to build and route a message.
 *
 * @param eventType      machine event key (e.g. "claim_submitted") used for template selection and routing
 * @param category       coarse category (e.g. "claims") used for per-user category opt-out checks
 * @param recipientEmail target user's email (may be blank for purely in-app/admin events)
 * @param relatedItemId  optional id of the entity the notification refers to (e.g. a found item)
 * @param link           in-app deep link the notification should point to
 * @param webhookEnabled whether a webhook delivery should also be attempted for this event
 * @param context        arbitrary key/value context used by templates and delivery records; never null
 */
public record RecoveryPulseEvent(
        String eventType,
        String category,
        String recipientEmail,
        String relatedItemId,
        String link,
        boolean webhookEnabled,
        Map<String, Object> context
) {
    /**
     * Compact canonical constructor that defends the {@code context} map: a null becomes an empty
     * immutable map, and any supplied map is defensively copied so the event is fully immutable.
     */
    public RecoveryPulseEvent {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
