package com.FBLA.WebCodingDev26Backend.dto;

/**
 * REQUEST DTO: parameters for triggering a test/simulated "Recovery Pulse"
 * notification dispatch.
 *
 * <p>Direction: client -> server (inbound request body for the recovery-pulse
 * test endpoint). Used by developers/staff to exercise the notification flow
 * without a real matching event.</p>
 */
public record RecoveryPulseTestRequest(
        // When true, simulate an inbound delivery-provider webhook callback
        // instead of (or in addition to) performing the dispatch. Nullable
        // (boxed Boolean) so an absent value can be treated as "not requested".
        Boolean simulateWebhook,
        // The webhook/delivery event type to simulate (e.g. "delivered",
        // "bounced"); interpreted only when simulateWebhook is true.
        String eventType
) {
}
