package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.RecoveryPulseDispatchResult;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
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

@Service
public class RecoveryPulseDispatcher {
    public static final String MODE_MOCK = "mock";
    public static final String MODE_LIVE = "live";

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final AppUserRepository users;
    private final ClockService clock;
    private final RecoveryPulseTemplateService templates;
    private final EmailNotificationProvider emailProvider;
    private final SmsNotificationProvider smsProvider;
    private final WebhookNotificationProvider webhookProvider;
    private final String mode;
    private final String adminEmail;

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
        this.mode = mode == null ? MODE_MOCK : mode.trim().toLowerCase(Locale.ROOT);
        this.adminEmail = normalizeEmail(adminEmail);
    }

    public RecoveryPulseDispatchResult dispatch(RecoveryPulseEvent event) {
        RecoveryPulseMessage message = templates.render(event);
        String now = clock.now();
        String recipientEmail = normalizeEmail(event.recipientEmail());

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

        List<NotificationDelivery> records = new ArrayList<>();
        records.add(saveFinalDelivery(saved, event, "in_app", "in_app", "sent", null, null, null, message.safePreview()));

        Optional<AppUser> user = blank(recipientEmail) ? Optional.empty() : users.findByEmail(recipientEmail);
        boolean categoryAllowed = categoryAllowed(user, event);
        records.add(deliverEmail(saved, event, message, user, categoryAllowed));
        records.add(deliverSms(saved, event, message, user, categoryAllowed));
        if (event.webhookEnabled() && webhookAllowed(user, categoryAllowed)) {
            records.add(deliverWebhook(saved, event, message, user));
        }

        return new RecoveryPulseDispatchResult(saved, records);
    }

    public RecoveryPulseDispatchResult dispatchToAdmin(String eventType, String category, String relatedItemId, String link, boolean webhookEnabled, Map<String, Object> context) {
        return dispatch(new RecoveryPulseEvent(eventType, category, adminEmail, relatedItemId, link, webhookEnabled, context));
    }

    public RecoveryPulseDispatchResult strongMatchAvailable(LostReport report, Map<String, Object> match) {
        if (report == null || blank(report.getContactEmail())) {
            return null;
        }
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

    public RecoveryPulseDispatchResult recoveryCaseStatusUpdated(RecoveryCase recoveryCase, String previousStatus) {
        if (recoveryCase == null) {
            return null;
        }
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

    public RecoveryPulseDispatchResult recoveryMissionAssigned(RecoveryMission mission) {
        if (mission == null || blank(mission.getAssignedTo())) {
            return null;
        }
        return dispatch(new RecoveryPulseEvent(
                "recovery_mission_assigned",
                "missions",
                mission.getAssignedTo(),
                mission.getRecoveryCaseId(),
                "/admin/recovery-cases/" + value(mission.getRecoveryCaseId()),
                true,
                Map.of("case_id", value(mission.getRecoveryCaseId()), "mission_id", value(mission.getId()))
        ));
    }

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

    private NotificationDelivery deliverEmail(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user, boolean categoryAllowed) {
        if (!categoryAllowed) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Notification category disabled.", null, message.safePreview());
        }
        if (user.isPresent() && Boolean.FALSE.equals(user.get().getEmailNotificationsEnabled())) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Email notifications disabled.", null, message.safePreview());
        }
        if (blank(notification.getUserEmail())) {
            return saveFinalDelivery(notification, event, "email", "email", "skipped", null, "Recipient email is missing.", null, message.safePreview());
        }
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "email", "mock_email", "mock_sent", "mock_email_" + shortId(), null, null, message.safePreview());
        }
        return sendWithProvider(notification, event, "email", "email", message.safePreview(),
                () -> emailProvider.send(new EmailMessage(notification.getUserEmail(), message.emailSubject(), message.emailBody())));
    }

    private NotificationDelivery deliverSms(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user, boolean categoryAllowed) {
        if (!categoryAllowed) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "Notification category disabled.", null, message.safePreview());
        }
        if (user.isEmpty() || !Boolean.TRUE.equals(user.get().getSmsOptIn()) || !Boolean.TRUE.equals(user.get().getSmsNotificationsEnabled())) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "SMS opt-in required.", null, message.safePreview());
        }
        String phone = user.get().getPhoneNumber();
        if (!isValidE164(phone)) {
            return saveFinalDelivery(notification, event, "sms", "sms", "skipped", null, "Valid E.164 phone number required.", null, message.safePreview());
        }
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "sms", "mock_sms", "mock_sent", "mock_sms_" + shortId(), null, maskPhone(phone), message.safePreview());
        }
        return sendWithProvider(notification, event, "sms", "sms", message.safePreview(),
                () -> smsProvider.send(new SmsMessage(phone, message.smsBody())));
    }

    private NotificationDelivery deliverWebhook(Notification notification, RecoveryPulseEvent event, RecoveryPulseMessage message, Optional<AppUser> user) {
        if (MODE_MOCK.equals(mode)) {
            return saveFinalDelivery(notification, event, "webhook", "mock_webhook", "mock_sent", "mock_webhook_" + shortId(), null, null, message.safePreview());
        }
        return sendWithProvider(notification, event, "webhook", "webhook", message.safePreview(),
                () -> webhookProvider.post(new WebhookMessage(webhookPayload(notification, event, message, user))));
    }

    private NotificationDelivery sendWithProvider(Notification notification, RecoveryPulseEvent event, String channel, String provider, String preview, ProviderCall call) {
        NotificationDelivery record = queuedDelivery(notification, event, channel, provider, preview, null);
        record = deliveries.save(record);
        try {
            DeliveryProviderResult result = call.send();
            record.setDeliveryStatus(valueOrDefault(result.status(), "failed"));
            record.setProviderMessageId(result.providerMessageId());
            record.setErrorMessage(result.errorMessage());
            if ("sent".equals(result.status())) {
                record.setSentDate(clock.now());
            }
        } catch (RuntimeException ex) {
            record.setDeliveryStatus("failed");
            record.setErrorMessage("Notification provider failed safely.");
        }
        return deliveries.save(record);
    }

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
        if (List.of("sent", "mock_sent").contains(status)) {
            record.setSentDate(clock.now());
        }
        return deliveries.save(record);
    }

    private NotificationDelivery queuedDelivery(Notification notification, RecoveryPulseEvent event, String channel, String provider, String preview, String maskedPhone) {
        NotificationDelivery record = new NotificationDelivery();
        record.setId("ndel_" + shortId());
        record.setNotificationId(notification.getId());
        record.setRecipientUserEmail(notification.getUserEmail());
        record.setRecipientEmail("email".equals(channel) ? notification.getUserEmail() : null);
        record.setRecipientPhoneMasked(maskedPhone);
        record.setChannel(channel);
        record.setEventType(event.eventType());
        record.setDeliveryStatus("queued");
        record.setProvider(provider);
        record.setCreatedDate(clock.now());
        record.setSafeMessagePreview(preview);
        record.setIsDemo(Boolean.TRUE.equals(event.context().get("is_demo")));
        return record;
    }

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
        user.ifPresent(appUser -> payload.put("recipient_role", appUser.getRole()));
        return payload;
    }

    private boolean webhookAllowed(Optional<AppUser> user, boolean categoryAllowed) {
        return categoryAllowed && (user.isEmpty() || !Boolean.FALSE.equals(user.get().getWebhookNotificationsEnabled()));
    }

    private boolean categoryAllowed(Optional<AppUser> user, RecoveryPulseEvent event) {
        if (user.isEmpty()) {
            return true;
        }
        List<String> categories = user.get().getNotificationCategories();
        if (categories == null || categories.isEmpty()) {
            return true;
        }
        String category = value(event.category()).toLowerCase(Locale.ROOT);
        String type = value(event.eventType()).toLowerCase(Locale.ROOT);
        return categories.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(category) || item.equals(type) || item.equals("all"));
    }

    public static boolean isValidE164(String phoneNumber) {
        return phoneNumber != null && E164.matcher(phoneNumber).matches();
    }

    private String maskPhone(String phone) {
        if (!isValidE164(phone)) {
            return null;
        }
        int visible = Math.min(4, phone.length());
        return phone.substring(0, 2) + "*****" + phone.substring(phone.length() - visible);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private String valueOrDefault(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface ProviderCall {
        DeliveryProviderResult send();
    }
}
