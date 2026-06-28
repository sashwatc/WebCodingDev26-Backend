package com.FBLA.WebCodingDev26Backend.service;

/**
 * Abstraction over an outbound webhook delivery channel, decoupling notification
 * logic from any concrete HTTP webhook implementation. Implementations POST a JSON
 * payload to a configured external URL so other systems can react to events.
 */
public interface WebhookNotificationProvider {
    /**
     * Attempts to POST the given webhook payload to the configured endpoint.
     *
     * @param message the JSON payload to deliver
     * @return a {@link DeliveryProviderResult} describing the outcome — sent,
     *         skipped (webhook disabled/unconfigured), or failed
     */
    DeliveryProviderResult post(WebhookMessage message);
}
