package com.FBLA.WebCodingDev26Backend.service;

/**
 * Immutable, fully-rendered notification content produced by {@link RecoveryPulseTemplateService}
 * for a single event. It holds one variant of the message per delivery channel so the dispatcher can
 * pick the right text without re-rendering.
 *
 * @param title          short headline (used for in-app title and as the email subject)
 * @param inAppMessage   body text shown inside the app
 * @param emailSubject   subject line for the email channel
 * @param emailBody      full body text for the email channel
 * @param smsBody        condensed text for the SMS channel
 * @param webhookSummary one-line summary placed in the webhook payload
 * @param safePreview    truncated, whitespace-collapsed preview safe to log/store on delivery records
 */
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
