package com.FBLA.WebCodingDev26Backend.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A search query saved by a user so they can re-run it or receive alerts when new matching
 * items appear.
 *
 * <p>Persisted as a MongoDB document in the "saved_searches" collection (mapped via
 * {@code @Document}). The set of search criteria is stored as a key/value map in
 * {@code filters}.</p>
 *
 * <p>Related models: owned by a user via the indexed {@code userId}.</p>
 */
@Document(collection = "saved_searches")
public class SavedSearch {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Id of the user who owns this saved search; indexed for lookup by user. */
    @Indexed
    private String userId;
    /** User-given display name for the saved search. */
    private String name;
    /** Search filter criteria as key/value pairs; uses LinkedHashMap to preserve order, defaults to empty. */
    private Map<String, String> filters = new LinkedHashMap<>();
    /** Timestamp (ISO string) when the search was saved. */
    private String createdAt;
    /** Whether new-match alerts are enabled for this search; defaults to false. */
    private Boolean alertsEnabled = false;

    // --- Trivial getters/setters; the filters setter is null-safe (see below). ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // Null-safe map setter: a null argument is coerced to an empty map so the field is never null.
    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters == null ? new LinkedHashMap<>() : filters; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Boolean getAlertsEnabled() { return alertsEnabled; }
    public void setAlertsEnabled(Boolean alertsEnabled) { this.alertsEnabled = alertsEnabled; }
}
