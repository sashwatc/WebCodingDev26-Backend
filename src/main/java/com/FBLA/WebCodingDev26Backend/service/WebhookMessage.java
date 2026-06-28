package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;

/**
 * Immutable value object wrapping the JSON body to be POSTed to an outbound
 * webhook endpoint by a {@link WebhookNotificationProvider}.
 *
 * <p>The {@code payload} is an arbitrary key/value map that the provider will
 * serialize to JSON and send to the configured webhook URL (e.g. for integration
 * with external systems such as Slack/Discord/automation pipelines).
 *
 * @param payload the structured event data to serialize and deliver
 */
public record WebhookMessage(Map<String, Object> payload) {
}
