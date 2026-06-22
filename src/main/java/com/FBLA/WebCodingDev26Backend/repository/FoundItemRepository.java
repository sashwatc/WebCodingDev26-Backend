package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FoundItemRepository extends MongoRepository<FoundItem, String> {
    List<FoundItem> findByStatus(String status);
    List<FoundItem> findByEventHubId(String eventHubId);
    List<FoundItem> findByCampusZoneId(String campusZoneId);
}
