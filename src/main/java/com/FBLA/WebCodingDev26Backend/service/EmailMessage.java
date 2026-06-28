package com.FBLA.WebCodingDev26Backend.service;

/**
 * Immutable value object describing a single outbound email to be delivered.
 *
 * <p>Acts as the transport-agnostic payload passed from notification senders to an
 * {@link EmailNotificationProvider}; it carries no delivery logic of its own.
 *
 * @param to      recipient email address
 * @param subject email subject line
 * @param body    email body content
 */
public record EmailMessage(String to, String subject, String body) {
}
