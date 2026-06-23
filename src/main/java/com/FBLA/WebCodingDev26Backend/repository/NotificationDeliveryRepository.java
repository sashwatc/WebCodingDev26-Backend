package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, String> {
    List<NotificationDelivery> findByNotificationId(String notificationId);
    List<NotificationDelivery> findByRecipientUserEmailOrderByCreatedDateDesc(String recipientUserEmail);
    List<NotificationDelivery> findByChannel(String channel);
}
