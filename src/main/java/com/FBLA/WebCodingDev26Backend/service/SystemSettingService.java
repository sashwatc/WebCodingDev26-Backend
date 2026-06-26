package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.SystemSetting;
import com.FBLA.WebCodingDev26Backend.repository.SystemSettingRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingService {
    private final SystemSettingRepository repository;

    @Autowired
    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> getAllAsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    public SystemSetting upsert(String key, String value, String updatedBy) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Setting key is required.");
        }
        SystemSetting setting = repository.findByKey(key).orElseGet(() -> {
            SystemSetting s = new SystemSetting();
            s.setId("ss_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            s.setKey(key);
            return s;
        });
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now().toString());
        setting.setUpdatedBy(updatedBy);
        return repository.save(setting);
    }

    public String get(String key, String defaultValue) {
        return repository.findByKey(key).map(SystemSetting::getValue).orElse(defaultValue);
    }

    /** Seed a setting only if it doesn't already exist. */
    public void seedIfAbsent(String key, String value) {
        if (repository.findByKey(key).isEmpty()) {
            upsert(key, value, "system");
        }
    }
}
