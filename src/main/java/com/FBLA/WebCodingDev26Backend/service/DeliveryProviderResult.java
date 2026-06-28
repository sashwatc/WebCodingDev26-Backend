package com.FBLA.WebCodingDev26Backend.service;

/**
 * Immutable outcome of a single notification-delivery attempt by a provider
 * (e.g. {@link ConfiguredWebhookNotificationProvider}). Normalizes the three possible
 * results — sent, skipped, failed — into one shape the notification layer can record.
 *
 * @param status            outcome status: {@code "sent"}, {@code "skipped"}, or {@code "failed"}
 * @param providerMessageId provider-assigned message id (only meaningful when sent; may be null)
 * @param errorMessage      reason for a skip or failure (null when sent)
 */
public record DeliveryProviderResult(String status, String providerMessageId, String errorMessage) {
    /** Builds a successful "sent" result carrying the optional provider message id. */
    public static DeliveryProviderResult sent(String providerMessageId) {
        return new DeliveryProviderResult("sent", providerMessageId, null);
    }

    /** Builds a "skipped" result (delivery intentionally not attempted) with the given reason. */
    public static DeliveryProviderResult skipped(String reason) {
        return new DeliveryProviderResult("skipped", null, reason);
    }

    /** Builds a "failed" result (delivery attempted but errored) with the given message. */
    public static DeliveryProviderResult failed(String message) {
        return new DeliveryProviderResult("failed", null, message);
    }
}
