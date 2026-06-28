package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * CampusZone is a named physical area/location on campus (e.g. "Gym", "Library") used to
 * categorize where items were found or lost. Spring Data MongoDB document stored in the
 * "campus_zones" collection.
 *
 * Referenced by {@link FoundItem#getCampusZoneId()} and {@link LostReport#getCampusZoneId()},
 * and grouped into events via {@link EventRecoveryHub#getCampusZoneIds()}.
 */
@Document(collection = "campus_zones")
public class CampusZone {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // Human-readable name of the zone. @Indexed(unique = true) keeps zone labels unique.
    @Indexed(unique = true)
    private String label;
    // Optional longer description of the zone.
    private String description;
    // Timestamp (ISO-8601 string) when this zone was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this zone was last updated.
    private String updatedDate;

    // --- standard getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
