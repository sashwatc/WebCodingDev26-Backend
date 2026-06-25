package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.NotificationPreferencesRequest;
import com.FBLA.WebCodingDev26Backend.dto.NotificationPreferencesResponse;
import com.FBLA.WebCodingDev26Backend.dto.RecoveryPulseDispatchResult;
import com.FBLA.WebCodingDev26Backend.dto.RecoveryPulseTestRequest;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.service.ClockService;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.RecoveryPulseDispatcher;
import com.FBLA.WebCodingDev26Backend.service.RecoveryPulseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery-pulse")
public class RecoveryPulseController {
    private final AppUserRepository users;
    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final DemoAuthorizationService authorization;
    private final RecoveryPulseDispatcher dispatcher;
    private final ClockService clock;

    public RecoveryPulseController(
            AppUserRepository users,
            NotificationRepository notifications,
            NotificationDeliveryRepository deliveries,
            DemoAuthorizationService authorization,
            RecoveryPulseDispatcher dispatcher,
            ClockService clock
    ) {
        this.users = users;
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.authorization = authorization;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @GetMapping("/preferences")
    public NotificationPreferencesResponse preferences(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        return response(requireUser(emailHeader));
    }

    @PatchMapping("/preferences")
    public NotificationPreferencesResponse updatePreferences(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestBody NotificationPreferencesRequest request
    ) {
        AppUser user = requireUser(emailHeader);
        if (request.phoneNumber() != null) {
            String phone = request.phoneNumber().trim();
            if (!phone.isBlank() && !RecoveryPulseDispatcher.isValidE164(phone)) {
                throw new BadRequestException("Phone number must be E.164 format, such as +15550123456.");
            }
            user.setPhoneNumber(phone.isBlank() ? null : phone);
        }
        if (request.emailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        }
        if (request.smsOptIn() != null) {
            user.setSmsOptIn(request.smsOptIn());
        }
        if (request.smsNotificationsEnabled() != null) {
            user.setSmsNotificationsEnabled(request.smsNotificationsEnabled());
        }
        if (request.webhookNotificationsEnabled() != null) {
            user.setWebhookNotificationsEnabled(request.webhookNotificationsEnabled());
        }
        if (request.notificationCategories() != null) {
            user.setNotificationCategories(safeCategories(request.notificationCategories()));
        }
        user.setUpdatedDate(clock.now());
        return response(users.save(user));
    }

    @GetMapping("/notifications")
    public List<Notification> notificationHistory(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        AppUser user = requireUser(emailHeader);
        return notifications.findByUserEmailOrderByCreatedDateDesc(user.getEmail());
    }

    @GetMapping("/deliveries")
    public List<NotificationDelivery> deliveryHistory(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        AppUser user = requireUser(emailHeader);
        return deliveries.findByRecipientUserEmailOrderByCreatedDateDesc(user.getEmail());
    }

    @GetMapping("/admin/deliveries")
    public List<NotificationDelivery> adminDeliveryHistory(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestParam(value = "channel", required = false) String channel
    ) {
        authorization.requireAdmin(emailHeader);
        if (channel == null || channel.isBlank()) {
            return deliveries.findAll();
        }
        return deliveries.findByChannel(channel.trim().toLowerCase(Locale.ROOT));
    }

    @PostMapping("/admin/test")
    public RecoveryPulseDispatchResult testNotification(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestBody(required = false) RecoveryPulseTestRequest request
    ) {
        AppUser admin = authorization.requireAdmin(emailHeader);
        String type = request == null || request.eventType() == null || request.eventType().isBlank()
                ? "pattern_review_alert"
                : request.eventType().trim().toLowerCase(Locale.ROOT);
        boolean webhook = request == null || !Boolean.FALSE.equals(request.simulateWebhook());
        return dispatcher.dispatch(new RecoveryPulseEvent(
                type,
                "pattern_review",
                admin.getEmail(),
                null,
                "/admin/pattern-review",
                webhook,
                Map.of("is_demo", true, "source", "admin_test")
        ));
    }

    private AppUser requireUser(String emailHeader) {
        String email = normalize(authorization.resolveEmail(emailHeader));
        if (email.isBlank()) {
            throw new ForbiddenException("Signed-in user is required.");
        }
        return users.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Signed-in user is required."));
    }

    private NotificationPreferencesResponse response(AppUser user) {
        return new NotificationPreferencesResponse(
                user.getEmail(),
                user.getPhoneNumber(),
                user.getEmailNotificationsEnabled(),
                user.getSmsOptIn(),
                user.getSmsNotificationsEnabled(),
                user.getWebhookNotificationsEnabled(),
                user.getNotificationCategories()
        );
    }

    private List<String> safeCategories(List<String> categories) {
        List<String> safe = new ArrayList<>();
        for (String category : categories) {
            if (category != null && !category.isBlank()) {
                safe.add(category.trim().toLowerCase(Locale.ROOT));
            }
        }
        return safe;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
