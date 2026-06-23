package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import java.util.List;

public record RecoveryPulseDispatchResult(
        Notification notification,
        List<NotificationDelivery> deliveries
) {
}
