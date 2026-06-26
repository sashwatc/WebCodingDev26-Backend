package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CaseMessageRepository extends MongoRepository<CaseMessage, String> {
    List<CaseMessage> findByClaimIdOrderByCreatedAtAsc(String claimId);
}
