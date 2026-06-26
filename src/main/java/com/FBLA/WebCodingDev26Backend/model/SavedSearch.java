package com.FBLA.WebCodingDev26Backend.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "saved_searches")
public class SavedSearch {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String name;
    private Map<String, String> filters = new LinkedHashMap<>();
    private String createdAt;
    private Boolean alertsEnabled = false;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters == null ? new LinkedHashMap<>() : filters; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Boolean getAlertsEnabled() { return alertsEnabled; }
    public void setAlertsEnabled(Boolean alertsEnabled) { this.alertsEnabled = alertsEnabled; }
}
