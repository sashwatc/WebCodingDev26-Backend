package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.WatchedItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link WatchedItem} entity
 * (a user's "watch"/follow on a found item). Extending {@link MongoRepository}
 * auto-generates the standard CRUD operations; only the custom methods below are declared.
 */
public interface WatchedItemRepository extends MongoRepository<WatchedItem, String> {
    // Derived query: returns all watched-item records whose "userId" equals the argument
    // (everything that user is watching), in unspecified order.
    List<WatchedItem> findByUserId(String userId);

    // Derived query: finds the single record matching BOTH "userId" AND "foundItemId"
    // (whether this user is watching this specific item). Returns Optional.empty() if not.
    Optional<WatchedItem> findByUserIdAndFoundItemId(String userId, String foundItemId);

    // Derived delete: removes the record(s) matching BOTH "userId" AND "foundItemId"
    // (used to un-watch a specific item for a user). Returns no value.
    void deleteByUserIdAndFoundItemId(String userId, String foundItemId);
}
