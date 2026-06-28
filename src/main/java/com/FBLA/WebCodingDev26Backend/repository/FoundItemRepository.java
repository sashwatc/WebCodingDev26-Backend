package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link FoundItem} entity (items turned in as found).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom queries below are declared.
 */
public interface FoundItemRepository extends MongoRepository<FoundItem, String> {
    // Derived query: returns all found items whose "status" equals the argument
    // (e.g. all items in a given lifecycle state), in unspecified order.
    List<FoundItem> findByStatus(String status);

    // Derived query: returns all found items whose "eventHubId" equals the argument
    // (all items collected at one event recovery hub), in unspecified order.
    List<FoundItem> findByEventHubId(String eventHubId);

    // Derived query: returns all found items whose "campusZoneId" equals the argument
    // (all items associated with one campus zone), in unspecified order.
    List<FoundItem> findByCampusZoneId(String campusZoneId);
}
