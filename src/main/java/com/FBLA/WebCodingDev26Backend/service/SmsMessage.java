package com.FBLA.WebCodingDev26Backend.service;

/**
 * Immutable value object describing a single outbound SMS to be delivered by an
 * {@link SmsNotificationProvider} (e.g. {@link TwilioSmsNotificationProvider}).
 *
 * <p>It carries only the two fields a provider needs to dispatch a text message;
 * the "from" number and credentials live in the provider's configuration, not here.
 *
 * @param to   the recipient's phone number (typically E.164 format, e.g. "+15551234567")
 * @param body the plain-text message content to send
 */
public record SmsMessage(String to, String body) {
}
