package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import java.util.List;

/**
 * INTERNAL RESULT DTO: the outcome of dispatching a "Recovery Pulse"
 * notification (the alert sent when a found item is matched to a claimant).
 *
 * <p>Direction: internal service return value; also serializable to clients in
 * notification/test responses. It is not a request payload.</p>
 *
 * <p>Bundles the single {@link Notification} record that was created together
 * with the list of per-channel {@link NotificationDelivery} attempts produced
 * when that notification was sent out.</p>
 */
public record RecoveryPulseDispatchResult(
        // The notification entity that was created/dispatched.
        Notification notification,
        // One delivery record per channel/recipient the notification was sent to
        // (e.g. email/SMS attempts and their statuses); may be empty.
        List<NotificationDelivery> deliveries
) {
}
