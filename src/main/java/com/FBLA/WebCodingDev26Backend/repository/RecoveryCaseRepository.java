package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecoveryCaseRepository extends MongoRepository<RecoveryCase, String> {
    Optional<RecoveryCase> findByLostReportId(String lostReportId);
}
