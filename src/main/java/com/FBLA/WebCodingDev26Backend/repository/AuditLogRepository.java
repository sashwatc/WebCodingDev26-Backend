package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
}
