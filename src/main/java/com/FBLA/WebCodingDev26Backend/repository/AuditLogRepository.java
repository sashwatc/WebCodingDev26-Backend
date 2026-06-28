package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link AuditLog} entity (audit trail records).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations
 * (save, findById, findAll, delete, count, etc.). No custom query methods are declared,
 * so the inherited CRUD API is all that is exposed.
 */
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
