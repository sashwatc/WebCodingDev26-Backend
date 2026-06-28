package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link RecoveryCase} entity (recovery case workflow records).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom lookup below is declared.
 */
public interface RecoveryCaseRepository extends MongoRepository<RecoveryCase, String> {
    // Derived query: finds the single recovery case whose "lostReportId" equals the argument
    // (the case opened for that lost report). Returns Optional.empty() if none exists.
    Optional<RecoveryCase> findByLostReportId(String lostReportId);
}
