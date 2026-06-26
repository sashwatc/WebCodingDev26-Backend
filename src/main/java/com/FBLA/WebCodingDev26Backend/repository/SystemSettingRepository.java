package com.FBLA.WebCodingDev26Backend.repository;

import com.FBLA.WebCodingDev26Backend.model.SystemSetting;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SystemSettingRepository extends MongoRepository<SystemSetting, String> {
    Optional<SystemSetting> findByKey(String key);
}
