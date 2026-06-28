package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A node in the recovery network — a physical location/point (e.g. a drop-off desk, kiosk,
 * or collection station) that participates in returning lost items.
 *
 * <p>Persisted as a MongoDB document in the "recovery_nodes" collection (mapped via
 * {@code @Document}). A lightweight reference entity describing where items can be handled.</p>
 */
@Document(collection = "recovery_nodes")
public class RecoveryNode {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Human-readable name of the node/location. */
    private String name;
    /** Classification of the node, e.g. "desk", "kiosk", "locker". */
    private String nodeType;
    /** Operational status of the node, e.g. "active", "inactive". */
    private String status;
    /** Timestamp (ISO string) when the node was created. */
    private String createdDate;
    /** Timestamp (ISO string) when the node was last updated. */
    private String updatedDate;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
