package com.FBLA.WebCodingDev26Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Concrete {@link WebhookNotificationProvider} that delivers notification payloads by
 * HTTP POST to an externally configured webhook URL. Acts as the outbound integration
 * point for webhook-style notifications; the URL and optional bearer secret come from
 * application config, so the provider is a no-op (skips) when no URL is set.
 *
 * <p>Collaborators: a Spring {@link RestClient} for the HTTP call; returns a
 * {@link DeliveryProviderResult} describing the outcome (sent / skipped / failed).
 */
@Service
public class ConfiguredWebhookNotificationProvider implements WebhookNotificationProvider {
    /** HTTP client used to POST the webhook payload. */
    private final RestClient restClient;
    /** Destination webhook URL; blank disables delivery (calls are skipped). */
    private final String webhookUrl;
    /** Optional shared secret; when present, sent as a {@code Bearer} Authorization header. */
    private final String webhookSecret;

    /** Injects the configured webhook URL/secret and builds the RestClient. No network calls. */
    public ConfiguredWebhookNotificationProvider(
            @Value("${app.notifications.webhook.url:}") String webhookUrl,
            @Value("${app.notifications.webhook.secret:}") String webhookSecret
    ) {
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Delivers a webhook message via HTTP POST.
     *
     * <p>Behavior: returns {@code skipped} when no URL is configured; otherwise POSTs the
     * message payload with an {@code X-Recovery-Pulse-Event} header carrying the event type,
     * adding a {@code Bearer} auth header when a secret is configured. Returns {@code sent}
     * on success or {@code failed} if the HTTP call throws.
     *
     * @param message the webhook message whose payload map is sent as the JSON body
     * @return a {@link DeliveryProviderResult} of sent / skipped / failed
     *         Side effect: an outbound HTTP request to the external webhook.
     */
    @Override
    public DeliveryProviderResult post(WebhookMessage message) {
        // Not configured: report skipped rather than attempting a call.
        if (blank(webhookUrl)) {
            return DeliveryProviderResult.skipped("Notification webhook is not configured.");
        }
        try {
            // Build the POST with the event-type header derived from the payload.
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(webhookUrl)
                    .header("X-Recovery-Pulse-Event", String.valueOf(message.payload().get("event_type")));
            // Attach bearer auth only when a secret is configured.
            if (!blank(webhookSecret)) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + webhookSecret);
            }
            // Send the payload as the body; response body is ignored.
            request.body(message.payload())
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryProviderResult.sent(null);
        } catch (RestClientException ex) {
            // Any transport/HTTP error is reported as a failed delivery.
            return DeliveryProviderResult.failed("Webhook delivery failed.");
        }
    }

    /** True when the string is null or blank. */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
