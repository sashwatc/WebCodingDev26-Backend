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

/**
 * {@link SmsNotificationProvider} implementation backed by Twilio's REST API.
 *
 * <p>It delivers SMS by POSTing a form-encoded request to Twilio's
 * {@code /2010-04-01/Accounts/{sid}/Messages.json} endpoint, authenticated with
 * HTTP Basic auth (Account SID + Auth Token). All settings are externalized via
 * {@code app.notifications.*} properties so the provider can be disabled or
 * pointed at a real Twilio account without code changes.
 *
 * <p>External collaborator: Twilio (api.twilio.com) over HTTPS via Spring's
 * {@link RestClient}.
 */
@Service
public class TwilioSmsNotificationProvider implements SmsNotificationProvider {
    /** Pre-built HTTP client targeting Twilio's API base URL. */
    private final RestClient restClient;
    /** Configured SMS provider name; this implementation only acts when it equals "twilio". */
    private final String provider;
    /** Twilio Account SID — both the Basic-auth username and a path segment in the API URL. */
    private final String accountSid;
    /** Twilio Auth Token — the Basic-auth password (secret). */
    private final String authToken;
    /** The Twilio-provisioned sender phone number used as the "From" field. */
    private final String fromNumber;

    /**
     * Builds the provider from externalized configuration and prepares the Twilio
     * REST client.
     *
     * <p>Each {@code @Value} uses a default after the colon: {@code provider}
     * defaults to "twilio", while the credentials/from-number default to empty
     * (which leaves the provider in an unconfigured/"skipped" state).
     *
     * @param provider   configured SMS provider key ({@code app.notifications.sms.provider})
     * @param accountSid Twilio Account SID ({@code app.notifications.twilio.account-sid})
     * @param authToken  Twilio Auth Token ({@code app.notifications.twilio.auth-token})
     * @param fromNumber Twilio sender number ({@code app.notifications.twilio.from-number})
     */
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
        // Construct the HTTP client once, rooted at Twilio's API host.
        this.restClient = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .build();
    }

    /**
     * Sends an SMS via Twilio, short-circuiting when the provider is disabled or
     * not fully configured.
     *
     * <p>Flow: (1) skip if the active provider isn't Twilio; (2) skip if any
     * credential/from-number is blank; (3) build the {@code From/To/Body}
     * form body; (4) POST it with Basic auth; (5) read Twilio's message {@code sid}
     * from the JSON response as the provider message id.
     *
     * @param message the recipient number and body to deliver
     * @return {@code skipped} when not Twilio or unconfigured; {@code sent} with the
     *         Twilio message SID on success; {@code failed} if the HTTP call errors
     */
    @Override
    public DeliveryProviderResult send(SmsMessage message) {
        // Only handle delivery when Twilio is the selected provider.
        if (!"twilio".equalsIgnoreCase(provider)) {
            return DeliveryProviderResult.skipped("SMS provider is not Twilio.");
        }
        // Require all credentials + a sender number before attempting a send.
        if (blank(accountSid) || blank(authToken) || blank(fromNumber)) {
            return DeliveryProviderResult.skipped("Twilio SMS provider is not configured.");
        }
        try {
            // Twilio expects application/x-www-form-urlencoded fields, not JSON.
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("From", fromNumber);
            form.add("To", message.to());
            form.add("Body", message.body());

            // POST to the Messages endpoint for this account; {sid} fills the path.
            // Authenticate via HTTP Basic (SID:token), and parse the JSON response as a Map.
            Map<?, ?> response = restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            // Twilio returns the created message's "sid" — surface it as the provider id.
            Object sid = response == null ? null : response.get("sid");
            return DeliveryProviderResult.sent(sid == null ? null : String.valueOf(sid));
        } catch (RestClientException ex) {
            // Any transport/HTTP error is reported as a (non-throwing) failed result.
            return DeliveryProviderResult.failed("Twilio SMS send failed.");
        }
    }

    /**
     * Builds an HTTP Basic {@code Authorization} header value from the given
     * credentials.
     *
     * @param username the Basic-auth username (Twilio Account SID)
     * @param password the Basic-auth password (Twilio Auth Token)
     * @return a header value of the form {@code "Basic <base64(username:password)>"}
     */
    private String basicAuth(String username, String password) {
        // Base64-encode "username:password" using UTF-8 bytes, per the Basic auth scheme.
        String token = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    /**
     * Null/blank string test helper.
     *
     * @param value the string to check
     * @return true if the value is null or contains only whitespace
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
