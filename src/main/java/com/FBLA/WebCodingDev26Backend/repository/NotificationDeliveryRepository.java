package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link NotificationDelivery} entity
 * (per-channel delivery attempts for a notification). Extending {@link MongoRepository}
 * auto-generates the standard CRUD operations; only the custom queries below are declared.
 */
public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, String> {
    // Derived query: returns all delivery records whose "notificationId" equals the argument
    // (all deliveries spawned by one notification), in unspecified order.
    List<NotificationDelivery> findByNotificationId(String notificationId);

    // Derived query: returns all delivery records whose "recipientUserEmail" equals the argument,
    // sorted by "createdDate" descending (newest first) — that recipient's delivery history.
    List<NotificationDelivery> findByRecipientUserEmailOrderByCreatedDateDesc(String recipientUserEmail);

    // Derived query: returns all delivery records whose "channel" equals the argument
    // (e.g. all email vs. SMS deliveries), in unspecified order.
    List<NotificationDelivery> findByChannel(String channel);
}
