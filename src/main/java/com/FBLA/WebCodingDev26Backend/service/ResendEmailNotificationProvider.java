package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ResendEmailNotificationProvider implements EmailNotificationProvider {
    private final RestClient restClient;
    private final String provider;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailNotificationProvider(
            @Value("${app.notifications.email.provider:resend}") String provider,
            @Value("${app.notifications.resend.api-key:}") String apiKey,
            @Value("${app.notifications.resend.from:lostthenfound@pvhs.demo}") String fromAddress
    ) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    @Override
    public DeliveryProviderResult send(EmailMessage message) {
        if (!"resend".equalsIgnoreCase(provider)) {
            return DeliveryProviderResult.skipped("Email provider is not Resend.");
        }
        if (blank(apiKey) || blank(fromAddress)) {
            return DeliveryProviderResult.skipped("Resend email provider is not configured.");
        }
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(Map.of(
                            "from", fromAddress,
                            "to", message.to(),
                            "subject", message.subject(),
                            "text", message.body()
                    ))
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("id");
            return DeliveryProviderResult.sent(id == null ? null : String.valueOf(id));
        } catch (RestClientException ex) {
            return DeliveryProviderResult.failed("Resend email send failed.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
