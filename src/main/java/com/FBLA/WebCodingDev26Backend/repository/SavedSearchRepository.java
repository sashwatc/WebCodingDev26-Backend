package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.SavedSearch;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link SavedSearch} entity (users' saved search criteria).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom queries below are declared.
 */
public interface SavedSearchRepository extends MongoRepository<SavedSearch, String> {
    // Derived query: returns all saved searches whose "userId" equals the argument
    // (that user's saved searches), in unspecified order.
    List<SavedSearch> findByUserId(String userId);

    // Derived query: returns all saved searches whose "alertsEnabled" flag equals the argument
    // (e.g. pass true to fetch every search that has alerting turned on), in unspecified order.
    List<SavedSearch> findByAlertsEnabled(Boolean alertsEnabled);
}
