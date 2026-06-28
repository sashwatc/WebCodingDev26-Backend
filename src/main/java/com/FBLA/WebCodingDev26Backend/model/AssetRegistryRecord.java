package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * AssetRegistryRecord represents an institutionally-owned/tagged asset (e.g. a
 * school-issued device) that has been registered so found items can be matched back to it.
 * Spring Data MongoDB document stored in the "asset_registry" collection.
 *
 * Linked from {@link FoundItem} via its assetTag / assetRecordId / departmentDestination
 * fields: when a found item carries a known asset tag, it can be routed to the owning department.
 */
@Document(collection = "asset_registry")
public class AssetRegistryRecord {
    // MongoDB document primary key (@Id); maps to Mongo _id.
    @Id
    private String id;
    // The physical asset tag/barcode identifying the asset. @Indexed(unique = true) guarantees
    // each asset tag appears at most once in the registry.
    @Indexed(unique = true)
    private String assetTag;
    // Kind/category of the asset (e.g. "laptop", "tablet", "calculator").
    private String assetType;
    // Department this asset belongs to / should be returned to when recovered.
    private String departmentDestination;
    // Lifecycle/registration status string for the asset (e.g. active/retired).
    private String status;
    // Timestamp (ISO-8601 string) when this registry record was created.
    private String createdDate;
    // Timestamp (ISO-8601 string) when this registry record was last updated.
    private String updatedDate;

    // --- standard getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getDepartmentDestination() { return departmentDestination; }
    public void setDepartmentDestination(String departmentDestination) { this.departmentDestination = departmentDestination; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
