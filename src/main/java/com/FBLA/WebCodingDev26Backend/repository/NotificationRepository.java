package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.Notification;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link Notification} entity (user-facing notifications).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom queries below are declared.
 */
public interface NotificationRepository extends MongoRepository<Notification, String> {
    // Derived query: returns all notifications whose "userEmail" equals the argument
    // (that user's notifications), in unspecified order.
    List<Notification> findByUserEmail(String userEmail);

    // Derived query: returns all notifications whose "userEmail" equals the argument,
    // sorted by "createdDate" descending (newest first) — that user's notification feed.
    List<Notification> findByUserEmailOrderByCreatedDateDesc(String userEmail);
}
