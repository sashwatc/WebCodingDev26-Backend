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
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the "Recovery Pulse" notification subsystem.
 *
 * <p>Base route: {@code /api/recovery-pulse}. Returns JSON (every handler is a
 * {@code @RestController} method whose return value is serialized to the response body).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read/update a signed-in user's notification preferences (phone number, email/SMS/webhook
 *       toggles, opt-in flags, and per-category subscriptions).</li>
 *   <li>Let a user read their notification history and mark notifications read / dismiss them.</li>
 *   <li>Expose a user's own notification-delivery audit trail, plus an admin-only view across all
 *       recipients and an admin-only "send a test notification" action.</li>
 * </ul>
 *
 * <p>Collaborators: {@link AppUserRepository} (user + preference persistence),
 * {@link NotificationRepository} and {@link NotificationDeliveryRepository} (history),
 * {@link DemoAuthorizationService} (resolves the caller from the {@code X-Demo-User-Email}
 * header and enforces staff/admin checks), {@link RecoveryPulseDispatcher} (sends notifications
 * across channels), and {@link ClockService} (testable "now" timestamps).
 *
 * <p>Authentication is demo-style: callers identify themselves via the
 * {@code X-Demo-User-Email} request header rather than a real session token.
 */
@RestController // marks this a JSON REST controller; return values become the HTTP response body
@RequestMapping("/api/recovery-pulse") // common path prefix for every endpoint below
public class RecoveryPulseController {
    /** Persists and looks up {@link AppUser} records, including notification preference fields. */
    private final AppUserRepository users;
    /** Stores user-facing notifications (history, read/dismiss state). */
    private final NotificationRepository notifications;
    /** Stores the per-channel delivery audit log (what was actually sent and via which channel). */
    private final NotificationDeliveryRepository deliveries;
    /** Resolves the caller's email from the demo header and enforces staff/admin authorization. */
    private final DemoAuthorizationService authorization;
    /** Sends notifications across channels (email/SMS/webhook); also exposes E.164 validation. */
    private final RecoveryPulseDispatcher dispatcher;
    /** Supplies the current timestamp (injected for testability) for "updated" bookkeeping. */
    private final ClockService clock;

    /**
     * Constructor injection of all collaborators (Spring wires these singletons automatically).
     */
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

    /**
     * GET {@code /api/recovery-pulse/preferences} — return the caller's current notification
     * preferences.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller (optional in
     *                    the binding, but required at runtime).
     * @return 200 OK with a {@link NotificationPreferencesResponse} snapshot of the user's settings.
     * @throws ForbiddenException (403) if no signed-in user can be resolved from the header.
     */
    @GetMapping("/preferences")
    public NotificationPreferencesResponse preferences(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        return response(requireUser(emailHeader));
    }

    /**
     * POST {@code /api/recovery-pulse/preferences} — update notification preferences.
     *
     * <p>Convenience alias for the PATCH handler (some clients can only send POST). Delegates
     * directly to {@link #updatePreferences}; same inputs, behavior, status, and errors.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @param request the partial preference update (any subset of fields).
     * @return 200 OK with the updated {@link NotificationPreferencesResponse}.
     */
    @PostMapping("/preferences")
    public NotificationPreferencesResponse updatePreferencesPost(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestBody NotificationPreferencesRequest request
    ) {
        return updatePreferences(emailHeader, request);
    }

    /**
     * PATCH {@code /api/recovery-pulse/preferences} — partially update the caller's notification
     * preferences.
     *
     * <p>Only non-null fields in the request body are applied (each field is an independent,
     * optional patch):
     * <ul>
     *   <li>{@code phoneNumber}: trimmed; a blank value clears the stored number, otherwise it must
     *       be valid E.164 (e.g. {@code +15550123456}) or a 400 is raised.</li>
     *   <li>{@code emailNotificationsEnabled}, {@code smsOptIn}, {@code smsNotificationsEnabled},
     *       {@code webhookNotificationsEnabled}: boolean channel toggles.</li>
     *   <li>{@code notificationCategories}: replaces the subscribed category list (sanitized to
     *       trimmed, lower-cased, non-blank entries).</li>
     * </ul>
     * Sets the user's updated timestamp from {@link ClockService} and persists the change.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @param request the partial preference update body.
     * @return 200 OK with the saved {@link NotificationPreferencesResponse}.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     * @throws BadRequestException (400) if a non-blank phone number is not E.164.
     */
    @PatchMapping("/preferences")
    public NotificationPreferencesResponse updatePreferences(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestBody NotificationPreferencesRequest request
    ) {
        AppUser user = requireUser(emailHeader);
        // Each block is applied only when the corresponding field is present (non-null) in the body.
        if (request.phoneNumber() != null) {
            String phone = request.phoneNumber().trim();
            // Reject malformed numbers, but allow a blank value through to clear the phone number.
            if (!phone.isBlank() && !RecoveryPulseDispatcher.isValidE164(phone)) {
                throw new BadRequestException("Phone number must be E.164 format, such as +15550123456.");
            }
            user.setPhoneNumber(phone.isBlank() ? null : phone); // blank clears, otherwise store
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
        user.setUpdatedDate(clock.now()); // stamp the edit time from the injected clock
        return response(users.save(user)); // persist and echo back the updated preferences
    }

    /**
     * POST {@code /api/recovery-pulse/notifications/{id}/read} — mark a single notification read.
     *
     * @param id the notification id (path variable).
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the updated {@link Notification} (read flag set, updated timestamp set).
     * @throws ForbiddenException (403) if no caller email is present, or the caller neither owns the
     *         notification nor is staff/admin.
     * @throws NotFoundException (404) if no notification has the given id.
     */
    @PostMapping("/notifications/{id}/read")
    public Notification markNotificationRead(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader
    ) {
        String email = normalize(authorization.resolveEmail(emailHeader));
        if (email.isBlank()) {
            throw new ForbiddenException("Signed-in user is required.");
        }
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        // Owner can act on their own notification; otherwise the caller must be staff/admin.
        if (!email.equals(normalize(notification.getUserEmail())) && !authorization.isStaffOrAdmin(emailHeader)) {
            throw new ForbiddenException("Access denied.");
        }
        notification.setIsRead(true);
        notification.setUpdatedDate(clock.now());
        return notifications.save(notification);
    }

    /**
     * DELETE {@code /api/recovery-pulse/notifications/{id}} — dismiss (permanently delete) a
     * notification.
     *
     * @param id the notification id (path variable).
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with a small confirmation map {@code {id, dismissed:true}}.
     * @throws ForbiddenException (403) if no caller email is present, or the caller neither owns the
     *         notification nor is staff/admin.
     * @throws NotFoundException (404) if no notification has the given id.
     */
    @DeleteMapping("/notifications/{id}")
    public Map<String, Object> dismissNotification(
            @PathVariable String id,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader
    ) {
        String email = normalize(authorization.resolveEmail(emailHeader));
        if (email.isBlank()) {
            throw new ForbiddenException("Signed-in user is required.");
        }
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        // Owner can dismiss their own notification; otherwise the caller must be staff/admin.
        if (!email.equals(normalize(notification.getUserEmail())) && !authorization.isStaffOrAdmin(emailHeader)) {
            throw new ForbiddenException("Access denied.");
        }
        notifications.deleteById(id);
        return Map.of("id", id, "dismissed", true);
    }

    /**
     * GET {@code /api/recovery-pulse/notifications} — the caller's full notification history.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the caller's {@link Notification}s, newest first.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
    @GetMapping("/notifications")
    public List<Notification> notificationHistory(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        AppUser user = requireUser(emailHeader);
        return notifications.findByUserEmailOrderByCreatedDateDesc(user.getEmail());
    }

    /**
     * GET {@code /api/recovery-pulse/deliveries} — the caller's own delivery audit trail (records of
     * notifications actually dispatched to them across channels).
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the caller's {@link NotificationDelivery} records, newest first.
     * @throws ForbiddenException (403) if no signed-in user can be resolved.
     */
    @GetMapping("/deliveries")
    public List<NotificationDelivery> deliveryHistory(@RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader) {
        AppUser user = requireUser(emailHeader);
        return deliveries.findByRecipientUserEmailOrderByCreatedDateDesc(user.getEmail());
    }

    /**
     * GET {@code /api/recovery-pulse/admin/deliveries} — admin-only view of delivery records across
     * all recipients, optionally filtered by channel.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller (must be admin).
     * @param channel optional query param; when present, filters to that channel (trimmed,
     *                lower-cased, e.g. {@code email}/{@code sms}/{@code webhook}); when absent/blank
     *                returns all deliveries.
     * @return 200 OK with the matching {@link NotificationDelivery} records.
     * @throws ForbiddenException (403) if the caller is not an admin.
     */
    @GetMapping("/admin/deliveries")
    public List<NotificationDelivery> adminDeliveryHistory(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestParam(value = "channel", required = false) String channel
    ) {
        authorization.requireAdmin(emailHeader); // 403 unless caller is an admin
        if (channel == null || channel.isBlank()) {
            return deliveries.findAll(); // no filter -> every delivery record
        }
        return deliveries.findByChannel(channel.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * POST {@code /api/recovery-pulse/admin/test} — admin-only "send a test notification" action used
     * to exercise the dispatch pipeline (e.g. from the pattern-review admin screen).
     *
     * <p>Builds a {@link RecoveryPulseEvent} addressed to the admin themselves and dispatches it.
     *
     * @param emailHeader the {@code X-Demo-User-Email} header identifying the caller (must be admin).
     * @param request optional body; {@code eventType} defaults to {@code "pattern_review_alert"} when
     *                missing/blank (trimmed + lower-cased otherwise), and {@code simulateWebhook}
     *                defaults to {@code true} unless explicitly {@code false}.
     * @return 200 OK with the {@link RecoveryPulseDispatchResult} describing what was sent per channel.
     * @throws ForbiddenException (403) if the caller is not an admin.
     */
    @PostMapping("/admin/test")
    public RecoveryPulseDispatchResult testNotification(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String emailHeader,
            @RequestBody(required = false) RecoveryPulseTestRequest request
    ) {
        AppUser admin = authorization.requireAdmin(emailHeader);
        // Default the event type when not supplied; otherwise normalize it.
        String type = request == null || request.eventType() == null || request.eventType().isBlank()
                ? "pattern_review_alert"
                : request.eventType().trim().toLowerCase(Locale.ROOT);
        // Simulate the webhook channel unless the caller explicitly opted out with simulateWebhook=false.
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

    /**
     * Resolve the caller to an {@link AppUser}, or fail with 403.
     *
     * @throws ForbiddenException if the header yields no email or no matching user exists.
     */
    private AppUser requireUser(String emailHeader) {
        String email = normalize(authorization.resolveEmail(emailHeader));
        if (email.isBlank()) {
            throw new ForbiddenException("Signed-in user is required.");
        }
        return users.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Signed-in user is required."));
    }

    /** Map an {@link AppUser}'s preference fields into the DTO returned to clients. */
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

    /** Sanitize a category list: drop null/blank entries, trim and lower-case the rest. */
    private List<String> safeCategories(List<String> categories) {
        List<String> safe = new ArrayList<>();
        for (String category : categories) {
            if (category != null && !category.isBlank()) {
                safe.add(category.trim().toLowerCase(Locale.ROOT));
            }
        }
        return safe;
    }

    /** Normalize an email for comparison: null-safe, trimmed, lower-cased. */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
