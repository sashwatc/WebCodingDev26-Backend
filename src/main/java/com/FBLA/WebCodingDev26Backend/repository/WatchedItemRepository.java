package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.WatchedItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WatchedItemRepository extends MongoRepository<WatchedItem, String> {
    List<WatchedItem> findByUserId(String userId);
    Optional<WatchedItem> findByUserIdAndFoundItemId(String userId, String foundItemId);
    void deleteByUserIdAndFoundItemId(String userId, String foundItemId);
}
