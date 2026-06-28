package com.FBLA.WebCodingDev26Backend.service;

/**
 * Abstraction over an SMS delivery channel, decoupling the notification logic
 * from any concrete texting vendor. The Twilio-backed implementation
 * ({@link TwilioSmsNotificationProvider}) is the production collaborator, but the
 * interface allows the provider to be swapped, mocked in tests, or disabled.
 */
public interface SmsNotificationProvider {
    /**
     * Attempts to deliver the given SMS through the underlying provider.
     *
     * @param message the recipient number and body to send
     * @return a {@link DeliveryProviderResult} describing the outcome — sent (with a
     *         provider message id), skipped (provider disabled/unconfigured), or failed
     */
    DeliveryProviderResult send(SmsMessage message);
}
