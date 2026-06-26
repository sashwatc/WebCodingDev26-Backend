package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.SavedSearch;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SavedSearchRepository extends MongoRepository<SavedSearch, String> {
    List<SavedSearch> findByUserId(String userId);
    List<SavedSearch> findByAlertsEnabled(Boolean alertsEnabled);
}
