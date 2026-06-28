package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link CustodyEvent} entity
 * (chain-of-custody events tracking an item's handoffs). Extending {@link MongoRepository}
 * auto-generates the standard CRUD operations; only the custom query below is declared.
 */
public interface CustodyEventRepository extends MongoRepository<CustodyEvent, String> {
    // Derived query: returns all custody events for the given "foundItemId",
    // sorted by "sequenceNumber" ascending — i.e. the custody chain in order from first to latest.
    List<CustodyEvent> findByFoundItemIdOrderBySequenceNumberAsc(String foundItemId);
}
