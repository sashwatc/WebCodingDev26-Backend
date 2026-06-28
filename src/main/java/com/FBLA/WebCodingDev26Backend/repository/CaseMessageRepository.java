package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link CaseMessage} entity (messages exchanged on a claim).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom query below is declared.
 */
public interface CaseMessageRepository extends MongoRepository<CaseMessage, String> {
    // Derived query: returns all messages whose "claimId" equals the argument,
    // sorted by "createdAt" ascending (oldest first) — i.e. the conversation in chronological order.
    List<CaseMessage> findByClaimIdOrderByCreatedAtAsc(String claimId);
}
