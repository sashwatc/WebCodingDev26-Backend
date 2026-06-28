package com.FBLA.WebCodingDev26Backend.service;

/**
 * Abstraction over an outbound email delivery channel (e.g. an SMTP relay or a
 * transactional-email API). Implementations encapsulate the provider-specific
 * transport so callers can send mail without knowing which backend is wired in.
 */
public interface EmailNotificationProvider {
    /**
     * Attempts to deliver a single email through the underlying provider.
     *
     * @param message the recipient, subject, and body to send
     * @return a {@code DeliveryProviderResult} describing the delivery outcome
     *         (success/failure and any provider metadata)
     */
    DeliveryProviderResult send(EmailMessage message);
}
