package com.FBLA.WebCodingDev26Backend.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class TwilioSmsNotificationProvider implements SmsNotificationProvider {
    private final RestClient restClient;
    private final String provider;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsNotificationProvider(
            @Value("${app.notifications.sms.provider:twilio}") String provider,
            @Value("${app.notifications.twilio.account-sid:}") String accountSid,
            @Value("${app.notifications.twilio.auth-token:}") String authToken,
            @Value("${app.notifications.twilio.from-number:}") String fromNumber
    ) {
        this.provider = provider;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .build();
    }

    @Override
    public DeliveryProviderResult send(SmsMessage message) {
        if (!"twilio".equalsIgnoreCase(provider)) {
            return DeliveryProviderResult.skipped("SMS provider is not Twilio.");
        }
        if (blank(accountSid) || blank(authToken) || blank(fromNumber)) {
            return DeliveryProviderResult.skipped("Twilio SMS provider is not configured.");
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("From", fromNumber);
            form.add("To", message.to());
            form.add("Body", message.body());

            Map<?, ?> response = restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object sid = response == null ? null : response.get("sid");
            return DeliveryProviderResult.sent(sid == null ? null : String.valueOf(sid));
        } catch (RestClientException ex) {
            return DeliveryProviderResult.failed("Twilio SMS send failed.");
        }
    }

    private String basicAuth(String username, String password) {
        String token = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
