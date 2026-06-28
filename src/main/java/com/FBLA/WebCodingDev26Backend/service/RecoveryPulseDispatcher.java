package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.RecoveryPulseDispatchResult;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Central notification engine ("Recovery Pulse") for the Lost Then Found system. It turns a
 * {@link RecoveryPulseEvent} into a persisted in-app {@link Notification} plus fan-out delivery
 * attempts across multiple channels (in-app, email, SMS, and optionally webhook), recording a
 * {@link NotificationDelivery} audit row for every channel.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Renders channel-specific copy via {@link RecoveryPulseTemplateService}.</li>
 *   <li>Respects per-user preferences: notification-category opt-outs, and email/SMS/webhook
 *       enable flags and SMS opt-in.</li>
 *   <li>Operates in "mock" mode (records simulated deliveries without calling providers) or "live"
 *       mode (invokes the real provider, failing safely).</li>
 *   <li>Exposes a family of typed convenience methods (claim/return-pass/case/pattern events) that
 *       build the appropriate event and route it to the right recipient.</li>
 * </ul>
 *
 * <p>Collaborators: {@link NotificationRepository}, {@link NotificationDeliveryRepository},
 * {@link AppUserRepository}, {@link ClockService}, {@link RecoveryPulseTemplateService}, and the
 * three provider interfaces ({@link EmailNotificationProvider}, {@link SmsNotificationProvider},
 * {@link WebhookNotificationProvider}).
 */
@Service
public class RecoveryPulseDispatcher {
    /** Mode value: simulate deliveries without calling external providers. */
    public static final String MODE_MOCK = "mock";
    /** Mode value: actually invoke the configured providers. */
    public static final String MODE_LIVE = "live";

    /** Validator for E.164 phone numbers (e.g. +14155550123), required before any SMS send. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    /** Persistence for the in-app notification records. */
    private final NotificationRepository notifications;
    /** Persistence for per-channel delivery audit rows. */
    private final NotificationDeliveryRepository deliveries;
    /** User lookup, used to resolve recipient preferences and contact details. */
    private final AppUserRepository users;
    /** Timestamp provider (injectable for deterministic tests). */
    private final ClockService clock;
    /** Renders the per-channel message copy for an event. */
    private final RecoveryPulseTemplateService templates;
    /** Live email transport provider. */
    private final EmailNotificationProvider emailProvider;
    /** Live SMS transport provider. */
    private final SmsNotificationProvider smsProvider;
    /** Live webhook transport provider. */
    private final WebhookNotificationProvider webhookProvider;
    /** Active mode, normalized to lowercase ("mock" or "live"); defaults to "mock". */
    private final String mode;
    /** Default admin recipient email for admin-targeted events, normalized to lowercase. */
    private final String adminEmail;

    /**
     * Wires the dispatcher with its repositories, template renderer, channel providers, and config.
     *
     * @param notifications  notification repository
     * @param deliveries     delivery-record repository
     * @param users          user repository (preferences/contacts)
     * @param clock          timestamp provider
     * @param templates      message template renderer
     * @param emailProvider  email transport
     * @param smsProvider    SMS transport
     * @param webhookProvider webhook transport
     * @param mode           configured mode ("mock"/"live"); null defaults to mock
     * @param adminEmail     default admin recipient address
     */
    public RecoveryPulseDispatcher(
            NotificationRepository notifications,
            NotificationDeliveryRepository deliveries,
            AppUserRepository users,
            ClockService clock,
            RecoveryPulseTemplateService templates,
            EmailNotificationProvider emailProvider,
            SmsNotificationProvider smsProvider,
            WebhookNotificationProvider webhookProvider,
            @Value("${app.notifications.mode:mock}") String mode,
            @Value("${app.admin-email:admin@pvhs.demo}") String adminEmail
    ) {
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.users = users;
        this.clock = clock;
        this.templates = templates;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
        this.webhookProvider = webhookProvider;
        // Normalize the mode so comparisons against MODE_MOCK/MODE_LIVE are reliable.
        this.mode = mode == null ? MODE_MOCK : mode.trim().toLowerCase(Locale.ROOT);
        this.adminEmail = normalizeEmail(adminEmail);
    }

    /**
     * Core dispatch routine: persists the in-app notification and attempts every applicable channel.
     *
     * <p>Steps:
     * <ol>
     *   <li>Render channel copy and capture the current time and normalized recipient email.</li>
     *   <li>Build and save the in-app {@link Notification}.</li>
     *   <li>Always record an in-app delivery row as "sent".</li>
     *   <li>Look up the recipient user (if any) and compute whether the event's category is allowed.</li>
     *   <li>Attempt email and SMS deliveries (each may skip based on preferences/config).</li>
     *   <li>If the event requests a webhook and the user/category permit it, attempt the webhook.</li>
     * </ol>
     *
     * <p>Side effects: writes one notification row and one delivery row per attempted channel.
     *
     * @param event the event to dispatch
     * @return the saved notification together with all delivery records
     */
    public RecoveryPulseDispatchResult dispatch(RecoveryPulseEvent event) {
        // Render the per-channel copy once and reuse it across channels.
        RecoveryPulseMessage message = templates.render(event);
        String now = clock.now();
        String recipientEmail = normalizeEmail(event.recipientEmail());

        // Build and persist the in-app notification the user sees in their feed.
        Notification notification = new Notification();
        notification.setId("notif_" + shortId());
        notification.setUserEmail(recipientEmail);
        notification.setTitle(message.title());
        notification.setMessage(message.inAppMessage());
        notification.setType(event.eventType());
        notification.setLink(event.link());
        notification.setRelatedItemId(event.relatedItemId());
        notification.setIsRead(false);
        notification.setCreatedDate(now);
        notification.setUpdatedDate(now);
        Notification saved = notifications.save(notification);

        // The in-app channel is always considered delivered immediately.
        List<NotificationDelivery> records = new ArrayList<>();
        records.add(saveFinalDelivery(saved, event, "in_app", "in_app", "sent", null, null, null, message.safePreview()));

        // Resolve the recipient (if known) and whether their category preferences allow this event.
        Optional<AppUser> user = blank(recipientEmail) ? Optional.empty() : users.findByEmail(recipientEmail);
        boolean categoryAllowed = categoryAllowed(user, event);
        records.add(deliverEmail(saved, event, message, user, categoryAllowed));
        records.add(deliverSms(saved, event, message, user, categoryAllowed));
        // Webhook only when the event opts in and the user/category don't forbid it.
        if (event.webhookEnabled() && webhookAllowed(user, categoryAllowed)) {
            records.add(deliverWebhook(saved, event, message, user));
        }

        return new RecoveryPulseDispatchResult(saved, records);
    }

    /**
     * Convenience wrapper that dispatches an event addressed to the configured admin recipient.
     *
     * @param eventType      event key for template selection
     * @param category       category for opt-out checks
     * @param relatedItemId  optional related entity id
     * @param link           in-app deep link
     * @param webhookEnabled whether to also attempt a webhook
     * @param context        template/delivery context
     * @return the dispatch result
     */
    public RecoveryPulseDispatchResult dispatchToAdmin(String eventType, String category, String relatedItemId, String link, boolean webhookEnabled, Map<String, Object> context) {
        return dispatch(new RecoveryPulseEvent(eventType, category, adminEmail, relatedItemId, link, webhookEnabled, context));
    }

    /**
     * Notifies a lost-report owner that a strong candidate match is ready to review.
     *
     * <p>No-op (returns null) if the report or its contact email is missing. Reads the matched found
     * item id tolerating either {@code found_item_id} or {@code foundItemId} keys, then dispatches a
     * "strong_item_match" event linking to the user dashboard.
     *
     * @param report the lost report whose owner to notify
     * @param match  the match details (may contain the found item id under either key)
     * @return the dispatch result, or null if there is no addressable recipient
     */
    public RecoveryPulseDispatchResult strongMatchAvailable(LostReport report, Map<String, Object> match) {
        if (report == null || blank(report.getContactEmail())) {
            return null;
        }
        // Read the found-item id tolerating snake_case first, then camelCase.
        Object foundItemId = match == null ? null : match.get("found_item_id");
        if (foundItemId == null && match != null) {
            foundItemId = match.get("foundItemId");
        }
        return dispatch(new RecoveryPulseEvent(
                "strong_item_match",
                "matches",
                report.getContactEmail(),
                value(foundItemId),
                "/UserDashboard",
                false,
                Map.of("lost_report_id", value(report.getId()), "found_item_id", value(foundItemId))
        ));
    }

    /**
     * Notifies staff (admin recipient) that a new ownership claim was submitted. Enables webhook so
     * external staff tooling can react. No-op if the claim is null.
     *
     * @param claim the submitted claim
     * @return the dispatch result, or null if claim is null
     */
    public RecoveryPulseDispatchResult claimSubmitted(Claim claim) {
        if (claim == null) {
            return null;
        }
        return dispatchToAdmin(
                "claim_submitted",
                "claims",
                claim.getFoundItemId(),
                "/admin/claims",
                true,
                Map.of("claim_id", value(claim.getId()), "found_item_id", value(claim.getFoundItemId()))
        );
    }

    /**
     * Notifies the claimant that their claim's status changed (e.g. approved/rejected/more-info).
     * The caller supplies the specific event type. No-op if the claim or its claimant email is
     * missing. Deep-links to the claimant's claim view.
     *
     * @param claim     the claim whose status changed
     * @param eventType the specific status-change event key (selects template copy)
     * @return the dispatch result, or null if there is no addressable claimant
     */
    public RecoveryPulseDispatchResult claimStatusChanged(Claim claim, String eventType) {
        if (claim == null || blank(claim.getClaimantEmail())) {
            return null;
        }
        return dispatch(new RecoveryPulseEvent(
                eventType,
                "claims",
                claim.getClaimantEmail(),
                claim.getFoundItemId(),
                "/claim?claim=" + value(claim.getId()),
                false,
                Map.of("claim_id", value(claim.getId()), "found_item_id", value(claim.getFoundItemId()))
        ));
    }

    /**
     * Notifies the claimant that their Return Pass is ready for secure pickup. No-op if the pass or
     * its claimant email is missing. Deep-links to the pass's pickup page.
     *
     * @param pass the issued return pass
     * @return the dispatch result, or null if there is no addressable claimant
     */
    public RecoveryPulseDispatchResult returnPassReady(ReturnPass pass) {
        if (pass == null || blank(pass.getClaimantEmail())) {
            return null;
        }
        return dispatch(new RecoveryPulseEvent(
                "return_pass_ready",
                "return_pass",
                pass.getClaimantEmail(),
                pass.getFoundItemId(),
                "/return-pass/" + value(pass.getId()),
                false,
                Map.of("claim_id", value(pass.getClaimId()), "return_pass_id", value(pass.getId()))
        ));
    }

    /**
     * Sends the claimant a reminder that their approved item is still awaiting pickup. No-op if the
     * pass or its claimant email is missing. Deep-links to the pass's pickup page.
     *
     * @param pass the active return pass
     * @return the dispatch result, or null if there is no addressable claimant
     */
    public RecoveryPulseDispatchResult pickupReminder(ReturnPass pass) {
        if (pass == null || blank(pass.getClaimantEmail())) {
            return null;
        }
        return dispatch(new RecoveryPulseEvent(
                "pickup_reminder",
                "return_pass",
                pass.getClaimantEmail(),
                pass.getFoundItemId(),
                "/return-pass/" + value(pass.getId()),
                false,
                Map.of("claim_id", value(pass.getClaimId()), "return_pass_id", value(pass.getId()))
        ));
    }

    /**
     * Notifies the claimant that their recovery is complete (item handed back). No-op if the pass or
     * its claimant email is missing. Deep-links to the user dashboard.
     *
     * @param pass the redeemed return pass
     * @return the dispatch result, or null if there is no addressable claimant
     */
    public RecoveryPulseDispatchResult itemReturned(ReturnPass pass) {
        if (pass == null || blank(pass.getClaimantEmail())) {
            return null;
        }
        return dispatch(new RecoveryPulseEvent(
                "item_returned",
                "return_pass",
                pass.getClaimantEmail(),
                pass.getFoundItemId(),
                "/UserDashboard",
                false,
                Map.of("claim_id", value(pass.getClaimId()), "return_pass_id", value(pass.getId()))
        ));
    }

    /**
     * Notifies the responsible staff member that a recovery case changed status. Routes to the
     * case's assigned staffer when present, otherwise to the admin recipient. Enables webhook and
     * includes both the previous and new status in the context. No-op if the case is null.
     *
     * @param recoveryCase   the case whose status changed (carrying the new status)
     * @param previousStatus the status before the change
     * @return the dispatch result, or null if the case is null
     */
    public RecoveryPulseDispatchResult recoveryCaseStatusUpdated(RecoveryCase recoveryCase, String previousStatus) {
        if (recoveryCase == null) {
            return null;
        }
        // Prefer the assigned staffer; fall back to the admin inbox.
        String recipient = !blank(recoveryCase.getAssignedTo()) ? recoveryCase.getAssignedTo() : adminEmail;
        return dispatch(new RecoveryPulseEvent(
                "recovery_case_status_update",
                "recovery_cases",
                recipient,
                recoveryCase.getSelectedFoundItemId(),
                "/admin/recovery-cases/" + value(recoveryCase.getId()),
                true,
                Map.of(
                        "case_id", value(recoveryCase.getId()),
                        "previous_status", value(previousStatus),
                        "status", value(recoveryCase.getStatus())
                )
        ));
    }

    /**
     * Alerts the admin that a loss pattern needs review. Enables webhook; tolerates a null alert by
     * sending empty identifiers. Deep-links to the pattern-review screen.
     *
     * @param alert the prevention/pattern alert (may be null)
     * @return the dispatch result
     */
    public RecoveryPulseDispatchResult patternReviewAlert(PreventionAlert alert) {
        return dispatchToAdmin(
                "pattern_review_alert",
                "pattern_review",
                alert == null ? null : alert.getId(),
                "/admin/pattern-review",
                true,
                Map.of("alert_id", alert == null ? "" : value(alert.getId()))
        );
    }

    /**
     * Attempts email delivery, short-circuiting to a "skipped" record when not permitted.
     *
     * <p>Skips (in order) if: the category is disabled, the user explicitly turned email off, or no
     * recipient email exists. In mock mode records a simulated "mock_sent". Otherwise calls the live
     * email provider via {@link #sendWithProvider}.
     *
     * @return the persisted delivery record for the email channel
     */
    private NotificationDelivery deliverEmail(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user, boolean categoryAllowed) {
        // Category opt-out wins over everything else.
        if (!categoryAllowed) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Notification category disabled.", null, message.safePreview());
        }
        // Respect an explicit per-user email opt-out.
        if (user.isPresent() && Boolean.FALSE.equals(user.get().getEmailNotificationsEnabled())) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Email notifications disabled.", null, message.safePreview());
        }
        // Cannot email without a destination address.
        if (blank(notification.getUserEmail())) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Recipient email is missing.", null, message.safePreview());
        }
        // Mock mode: record a simulated send without touching the provider.
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "email", "mock_email", "mock_sent", "mock_email_" + shortId(), null, null, message.safePreview());
        }
        // Live mode: invoke the real provider.
        return sendWithProvider(notification, event, "email", "email", message.safePreview(),
                () -> emailProvider.send(new EmailMessage(notification.getUserEmail(), message.emailSubject(), message.emailBody())));
    }

    /**
     * Attempts SMS delivery, short-circuiting to a "skipped" record when not permitted.
     *
     * <p>Skips (in order) if: the category is disabled; the user is unknown or hasn't both opted in
     * to SMS and enabled SMS notifications; or the stored phone number is not valid E.164. In mock
     * mode records a simulated "mock_sent" with a masked phone. Otherwise calls the live SMS
     * provider via {@link #sendWithProvider}.
     *
     * @return the persisted delivery record for the SMS channel
     */
    private NotificationDelivery deliverSms(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user, boolean categoryAllowed) {
        // Category opt-out.
        if (!categoryAllowed) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "Notification category disabled.", null, message.safePreview());
        }
        // SMS requires a known user who has both opted in and enabled SMS notifications.
        if (user.isEmpty() || !Boolean.TRUE.equals(user.get().getSmsOptIn()) || !Boolean.TRUE.equals(user.get().getSmsNotificationsEnabled())) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "SMS opt-in required.", null, message.safePreview());
        }
        String phone = user.get().getPhoneNumber();
        // The phone number must be valid E.164 to send.
        if (!isValidE164(phone)) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "Valid E.164 phone number required.", null, message.safePreview());
        }
        // Mock mode: simulated send, storing only a masked phone number.
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "sms", "mock_sms", "mock_sent", "mock_sms_" + shortId(), null, maskPhone(phone), message.safePreview());
        }
        // Live mode: invoke the real provider.
        return sendWithProvider(notification, event, "sms", "sms", message.safePreview(),
                () -> smsProvider.send(new SmsMessage(phone, message.smsBody())));
    }

    /**
     * Attempts webhook delivery. In mock mode records a simulated "mock_sent"; otherwise posts the
     * assembled JSON payload to the live webhook provider via {@link #sendWithProvider}.
     *
     * @return the persisted delivery record for the webhook channel
     */
    private NotificationDelivery deliverWebhook(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user) {
        // Mock mode: simulated webhook send.
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "webhook", "mock_webhook", "mock_sent", "mock_webhook_" + shortId(), null, null, message.safePreview());
        }
        // Live mode: build the payload and POST it.
        return sendWithProvider(notification, event, "webhook", "webhook", message.safePreview(),
                () -> webhookProvider.post(new WebhookMessage(webhookPayload(notification, event, message, user))));
    }

    /**
     * Runs a live provider call inside a fail-safe envelope.
     *
     * <p>First persists a "queued" delivery row, then invokes the provider. On success the row is
     * updated with the provider's status, message id, and error (stamping a sent time only when the
     * status is exactly "sent"). Any thrown {@link RuntimeException} is caught and recorded as a
     * "failed" delivery so a provider outage can never propagate out of the dispatcher. The row is
     * re-saved and returned.
     *
     * @param channel  logical channel name (email/sms/webhook)
     * @param provider provider label recorded on the row
     * @param preview  safe message preview to store
     * @param call     the provider invocation to execute
     * @return the final persisted delivery record
     */
    private NotificationDelivery sendWithProvider(Notification notification, RecoveryPulseEvent event, String channel, String provider, String preview, ProviderCall call) {
        // Persist a queued row first so the attempt is auditable even if the call hangs/fails.
        NotificationDelivery record = queuedDelivery(notification, event, channel, provider, preview, null);
        record = deliveries.save(record);
        try {
            DeliveryProviderResult result = call.send();
            // Default to "failed" if the provider returned no explicit status.
            record.setDeliveryStatus(valueOrDefault(result.status(), "failed"));
            record.setProviderMessageId(result.providerMessageId());
            record.setErrorMessage(result.errorMessage());
            // Only a true "sent" stamps a sent timestamp.
            if ("sent".equals(result.status())) {
                record.setSentDate(clock.now());
            }
        } catch (RuntimeException ex) {
            // Fail safely: never let a provider exception escape.
            record.setDeliveryStatus("failed");
            record.setErrorMessage("Notification provider failed safely.");
        }
        return deliveries.save(record);
    }

    /**
     * Builds a delivery row already in its terminal state (used for in-app, skipped, and mock
     * outcomes that don't go through a live provider). Stamps a sent time for "sent"/"mock_sent".
     *
     * @param status            terminal delivery status to record
     * @param providerMessageId optional provider/mock message id
     * @param errorMessage      optional skip/error reason
     * @param maskedPhone       optional masked phone (SMS only)
     * @param preview           safe message preview
     * @return the persisted delivery record
     */
    private NotificationDelivery saveFinalDelivery(
            Notification notification,
            RecoveryPulseEvent event,
            String channel,
            String provider,
            String status,
            String providerMessageId,
            String errorMessage,
            String maskedPhone,
            String preview
    ) {
        NotificationDelivery record = queuedDelivery(notification, event, channel, provider, preview, maskedPhone);
        record.setDeliveryStatus(status);
        record.setProviderMessageId(providerMessageId);
        record.setErrorMessage(errorMessage);
        // Both real and simulated successful sends get a sent timestamp.
        if (List.of("sent", "mock_sent").contains(status)) {
            record.setSentDate(clock.now());
        }
        return deliveries.save(record);
    }

    /**
     * Constructs a fresh, unsaved delivery record initialized to "queued" with common fields
     * populated (id, notification linkage, recipient email/phone, channel, event type, provider,
     * created time, preview, and demo flag derived from the event context). The recipient email is
     * only set on the email channel.
     *
     * @return the new in-memory delivery record
     */
    private NotificationDelivery queuedDelivery(Notification notification, RecoveryPulseEvent event, String channel, String provider, String preview, String maskedPhone) {
        NotificationDelivery record = new NotificationDelivery();
        record.setId("ndel_" + shortId());
        record.setNotificationId(notification.getId());
        record.setRecipientUserEmail(notification.getUserEmail());
        // Only the email channel carries an explicit recipient email field.
        record.setRecipientEmail("email".equals(channel) ? notification.getUserEmail() : null);
        record.setRecipientPhoneMasked(maskedPhone);
        record.setChannel(channel);
        record.setEventType(event.eventType());
        record.setDeliveryStatus("queued");
        record.setProvider(provider);
        record.setCreatedDate(clock.now());
        record.setSafeMessagePreview(preview);
        // Propagate the demo marker so demo deliveries stay separable.
        record.setIsDemo(Boolean.TRUE.equals(event.context().get("is_demo")));
        return record;
    }

    /**
     * Assembles the JSON payload posted to webhook subscribers: event metadata, recipient, related
     * item, message title/summary, link, creation time, and (when known) the recipient's role. Uses
     * a {@link LinkedHashMap} for stable key ordering.
     *
     * @return the ordered payload map
     */
    private Map<String, Object> webhookPayload(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", event.eventType());
        payload.put("category", event.category());
        payload.put("notification_id", notification.getId());
        payload.put("recipient_user_email", notification.getUserEmail());
        payload.put("related_item_id", event.relatedItemId());
        payload.put("title", message.title());
        payload.put("summary", message.webhookSummary());
        payload.put("link", event.link());
        payload.put("created_date", notification.getCreatedDate());
        // Include the recipient's role only when the user is known.
        user.ifPresent(appUser -> payload.put("recipient_role", appUser.getRole()));
        return payload;
    }

    /**
     * Determines whether a webhook delivery is allowed: the category must be allowed AND the user
     * must either be unknown or not have explicitly disabled webhook notifications.
     */
    private boolean webhookAllowed(Optional<AppUser> user, boolean categoryAllowed) {
        return categoryAllowed && (user.isEmpty() || !Boolean.FALSE.equals(user.get().getWebhookNotificationsEnabled()));
    }

    /**
     * Determines whether the user's notification-category preferences permit this event.
     *
     * <p>Unknown users, or users with no category preferences set, are allowed everything. Otherwise
     * the event's category or event type must appear in the user's (normalized) category list, or
     * the list must contain the wildcard "all".
     *
     * @return true if the event's category/type is permitted for the user
     */
    private boolean categoryAllowed(Optional<AppUser> user, RecoveryPulseEvent event) {
        // No user context: permit by default.
        if (user.isEmpty()) {
            return true;
        }
        List<String> categories = user.get().getNotificationCategories();
        // No explicit preferences: permit everything.
        if (categories == null || categories.isEmpty()) {
            return true;
        }
        String category = value(event.category()).toLowerCase(Locale.ROOT);
        String type = value(event.eventType()).toLowerCase(Locale.ROOT);
        // Allow if any preference matches the category, the event type, or the wildcard "all".
        return categories.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(category) || item.equals(type) || item.equals("all"));
    }

    /** Public helper: returns true if the string is a valid E.164 phone number. */
    public static boolean isValidE164(String phoneNumber) {
        return phoneNumber != null && E164.matcher(phoneNumber).matches();
    }

    /**
     * Masks a valid E.164 phone number for safe storage/logging, keeping the first two and last
     * (up to four) digits and replacing the middle with asterisks. Returns null for invalid numbers.
     */
    private String maskPhone(String phone) {
        if (!isValidE164(phone)) {
            return null;
        }
        int visible = Math.min(4, phone.length());
        return phone.substring(0, 2) + "*****" + phone.substring(phone.length() - visible);
    }

    /** Generates a 10-character lowercase id fragment from a random UUID (dashes removed). */
    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** Lowercases and trims an email for consistent comparison/storage; null becomes empty. */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Null-safe stringification: null becomes an empty string. */
    private String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    /** Returns {@code value} if non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    /** Null-safe blank check. */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Functional interface representing a single live provider send call passed to {@link #sendWithProvider}. */
    @FunctionalInterface
    private interface ProviderCall {
        DeliveryProviderResult send();
    }
}
