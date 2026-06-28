package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link EmailNotificationProvider} implementation that sends transactional email through the
 * Resend HTTP API. Used by {@link RecoveryPulseDispatcher} when running in live (non-mock) mode.
 *
 * <p>It is intentionally fail-safe: misconfiguration yields a "skipped" result and any transport
 * error yields a "failed" result, never a thrown exception, so a broken email setup cannot break
 * the notification pipeline.
 */
@Service
public class ResendEmailNotificationProvider implements EmailNotificationProvider {
    /** Pre-built REST client pinned to the Resend API base URL. */
    private final RestClient restClient;
    /** Configured email provider name; this implementation only acts when it equals "resend". */
    private final String provider;
    /** Resend API key (bearer token); blank means the provider is unconfigured. */
    private final String apiKey;
    /** Verified "from" address used as the sender; blank means unconfigured. */
    private final String fromAddress;

    /**
     * Reads provider configuration from application properties (with defaults) and builds the REST
     * client targeting {@code https://api.resend.com}.
     *
     * @param provider    configured email provider name (default "resend")
     * @param apiKey      Resend API key (default empty / unconfigured)
     * @param fromAddress sender address (default "lostthenfound@pvhs.demo")
     */
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

    /**
     * Sends one email via Resend.
     *
     * <p>Returns "skipped" if this isn't the configured provider or if the API key / sender are not
     * configured. Otherwise POSTs to {@code /emails} with a bearer token and a from/to/subject/text
     * body, returning "sent" (with the Resend message id when present). Any
     * {@link RestClientException} is caught and converted to a "failed" result.
     *
     * @param message the recipient, subject, and body to send
     * @return a {@link DeliveryProviderResult} of skipped, sent, or failed (never throws)
     */
    @Override
    public DeliveryProviderResult send(EmailMessage message) {
        // Not our provider — let the dispatcher record a skip.
        if (!"resend".equalsIgnoreCase(provider)) {
            return DeliveryProviderResult.skipped("Email provider is not Resend.");
        }
        // Missing credentials/sender — cannot send, skip safely.
        if (blank(apiKey) || blank(fromAddress)) {
            return DeliveryProviderResult.skipped("Resend email provider is not configured.");
        }
        try {
            // POST the email; Resend replies with a JSON object containing the message "id".
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
            // Surface the provider message id when available for delivery tracking.
            Object id = response == null ? null : response.get("id");
            return DeliveryProviderResult.sent(id == null ? null : String.valueOf(id));
        } catch (RestClientException ex) {
            // Network/HTTP failure — report failed without leaking exception details.
            return DeliveryProviderResult.failed("Resend email send failed.");
        }
    }

    /** Null-safe blank check. */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
