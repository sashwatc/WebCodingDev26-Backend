package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.SystemSetting;
import com.FBLA.WebCodingDev26Backend.repository.SystemSettingRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manages the application's dynamic, key/value system settings — configuration
 * that admins can change at runtime (and that the app seeds with defaults at
 * startup) without redeploying.
 *
 * <p>Responsibilities: read all settings as a flat map, look up a single value
 * with a default, create-or-update (upsert) a setting while stamping who/when it
 * changed, and seed defaults idempotently.
 *
 * <p>Collaborators: {@link SystemSettingRepository} for persistence.
 */
@Service
public class SystemSettingService {
    /** Persistence gateway for {@link SystemSetting} rows, keyed by their string key. */
    private final SystemSettingRepository repository;

    /**
     * Constructs the service with its repository (injected by Spring).
     *
     * @param repository persistence gateway for system settings
     */
    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads every persisted setting into a flat key→value map.
     *
     * @return a {@link LinkedHashMap} (preserving repository iteration order) of all
     *         setting keys to their current string values
     */
    public Map<String, String> getAllAsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        // Collapse each persisted SystemSetting row into a key/value pair.
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    /**
     * Creates a new setting or updates the existing one for the given key, then
     * records the value, the update timestamp, and the actor who changed it.
     *
     * @param key       the unique setting key (required, non-blank)
     * @param value     the new value to store (may be null)
     * @param updatedBy identifier of who performed the change (e.g. an email or "system")
     * @return the saved {@link SystemSetting}
     * @throws BadRequestException if {@code key} is null or blank
     */
    public SystemSetting upsert(String key, String value, String updatedBy) {
        // Validation: a key is mandatory to address the setting.
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Setting key is required.");
        }
        // Find the existing setting by key, or build a new one with a generated id.
        SystemSetting setting = repository.findByKey(key).orElseGet(() -> {
            SystemSetting s = new SystemSetting();
            // Generate a compact, prefixed id from a random UUID (no dashes, 10 chars).
            s.setId("ss_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            s.setKey(key);
            return s;
        });
        // Apply the new value and audit metadata (ISO-8601 timestamp + actor).
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now().toString());
        setting.setUpdatedBy(updatedBy);
        return repository.save(setting);
    }

    /**
     * Looks up a single setting value, falling back to a default when absent.
     *
     * @param key          the setting key to read
     * @param defaultValue the value returned when no setting exists for the key
     * @return the stored value, or {@code defaultValue} if the key is not present
     */
    public String get(String key, String defaultValue) {
        return repository.findByKey(key).map(setting -> setting.getValue()).orElse(defaultValue);
    }

    /**
     * Seed a setting only if it doesn't already exist.
     *
     * <p>Idempotent: if a setting with the key is already present it is left
     * untouched (so admin edits survive restarts); otherwise it is created via
     * {@link #upsert} attributed to the "system" actor.
     *
     * @param key   the setting key to seed
     * @param value the default value to store when the key is absent
     */
    public void seedIfAbsent(String key, String value) {
        // Only write when the key is missing, preserving any existing/admin-edited value.
        if (repository.findByKey(key).isEmpty()) {
            upsert(key, value, "system");
        }
    }
}
