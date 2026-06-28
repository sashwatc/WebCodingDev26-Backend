package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A join record marking that a user is "watching"/following a particular found item (e.g. to
 * be notified of updates to it).
 *
 * <p>Persisted as a MongoDB document in the "watched_items" collection (mapped via
 * {@code @Document}). Each document represents one user-to-item watch relationship.</p>
 *
 * <p>Related models: links a user (indexed {@code userId}) to a found item
 * (indexed {@code foundItemId}).</p>
 */
@Document(collection = "watched_items")
public class WatchedItem {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Id of the user doing the watching; indexed for lookup by user. */
    @Indexed
    private String userId;
    /** Id of the found item being watched; indexed for lookup by item. */
    @Indexed
    private String foundItemId;
    /** Timestamp (ISO string) when the user started watching the item. */
    private String createdAt;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFoundItemId() { return foundItemId; }
    public void setFoundItemId(String foundItemId) { this.foundItemId = foundItemId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
