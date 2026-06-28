package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A single application configuration setting stored as a key/value pair.
 *
 * <p>Persisted as a MongoDB document in the "system_settings" collection (mapped via
 * {@code @Document}). Acts as a simple persistent key/value store for runtime-configurable
 * options, with audit fields recording who last changed each setting and when.</p>
 */
@Document(collection = "system_settings")
public class SystemSetting {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** The setting's unique name/key; uniquely indexed so each key appears at most once. */
    @Indexed(unique = true)
    private String key;
    /** The setting's stored value (serialized as a string). */
    private String value;
    /** Timestamp (ISO string) when this setting was last updated. */
    private String updatedAt;
    /** Identifier (e.g. admin email) of who last updated this setting. */
    private String updatedBy;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
