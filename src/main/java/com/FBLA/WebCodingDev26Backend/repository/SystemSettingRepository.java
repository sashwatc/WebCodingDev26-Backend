package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.SystemSetting;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for the {@link SystemSetting} entity (key/value configuration settings).
 * Extending {@link MongoRepository} auto-generates the standard CRUD operations;
 * only the custom lookup below is declared.
 */
public interface SystemSettingRepository extends MongoRepository<SystemSetting, String> {
    // Derived query: finds the single setting whose "key" field equals the argument.
    // Returns Optional.empty() if no setting has that key.
    Optional<SystemSetting> findByKey(String key);
}
