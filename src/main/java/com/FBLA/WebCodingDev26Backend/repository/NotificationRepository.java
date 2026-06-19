package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
}
