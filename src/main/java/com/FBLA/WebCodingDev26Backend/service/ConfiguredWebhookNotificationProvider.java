package com.FBLA.WebCodingDev26Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ConfiguredWebhookNotificationProvider implements WebhookNotificationProvider {
    private final RestClient restClient;
    private final String webhookUrl;
    private final String webhookSecret;

    public ConfiguredWebhookNotificationProvider(
            @Value("${app.notifications.webhook.url:}") String webhookUrl,
            @Value("${app.notifications.webhook.secret:}") String webhookSecret
    ) {
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public DeliveryProviderResult post(WebhookMessage message) {
        if (blank(webhookUrl)) {
            return DeliveryProviderResult.skipped("Notification webhook is not configured.");
        }
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(webhookUrl)
                    .header("X-Recovery-Pulse-Event", String.valueOf(message.payload().get("event_type")));
            if (!blank(webhookSecret)) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + webhookSecret);
            }
            request.body(message.payload())
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryProviderResult.sent(null);
        } catch (RestClientException ex) {
            return DeliveryProviderResult.failed("Webhook delivery failed.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
