package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.SavedSearch;
import com.FBLA.WebCodingDev26Backend.repository.SavedSearchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages users' saved searches and the alerting that fires when a newly-published found item
 * matches one of those saved searches.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>CRUD over {@link SavedSearch} records scoped to a user, enforcing per-owner access on
 *       update/delete.</li>
 *   <li>When a new found item is published, evaluate every alert-enabled saved search against it
 *       and notify the owning user on a match ({@link #checkAlertsForNewItem(FoundItem)}).</li>
 * </ul>
 *
 * <p>Collaborators: {@link SavedSearchRepository} (persistence/queries) and
 * {@link RecoveryPulseDispatcher} (notification delivery).
 */
@Service
public class SavedSearchService {
    /** Logger used to record (and swallow) failures while evaluating alerts. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SavedSearchService.class);

    /** Persistence for saved searches. */
    private final SavedSearchRepository repository;
    /** Dispatcher used to send "saved search match" notifications; may be null (alerting disabled). */
    private final RecoveryPulseDispatcher recoveryPulse;

    /**
     * Constructs the service.
     *
     * @param repository    saved-search repository
     * @param recoveryPulse notification dispatcher (may be null to disable alerts)
     */
    public SavedSearchService(SavedSearchRepository repository, RecoveryPulseDispatcher recoveryPulse) {
        this.repository = repository;
        this.recoveryPulse = recoveryPulse;
    }

    /** Returns all saved searches belonging to the given user. */
    public List<SavedSearch> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Creates and persists a new saved search for a user.
     *
     * <p>Generates an "ss_"-prefixed id, trims the name, stores the filter map, records the alerts
     * flag (null treated as false), and stamps the creation time.
     *
     * @param userId        owner of the saved search
     * @param name          display name (required, non-blank)
     * @param filters       search filter criteria
     * @param alertsEnabled whether to notify the user on new matching items (null = false)
     * @return the saved search
     * @throws BadRequestException if the name is missing or blank
     */
    public SavedSearch create(String userId, String name, Map<String, String> filters, Boolean alertsEnabled) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Name is required for a saved search.");
        }
        SavedSearch search = new SavedSearch();
        search.setId("ss_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        search.setUserId(userId);
        search.setName(name.trim());
        search.setFilters(filters);
        search.setAlertsEnabled(Boolean.TRUE.equals(alertsEnabled));
        search.setCreatedAt(Instant.now().toString());
        return repository.save(search);
    }

    /**
     * Updates the name and/or alerts flag of an existing saved search, enforcing owner access.
     *
     * <p>Only non-null fields are applied: a non-blank name (trimmed) and a non-null alerts flag.
     *
     * @param id            id of the saved search
     * @param userId        id of the requesting user (must own the search)
     * @param name          new name, or null/blank to leave unchanged
     * @param alertsEnabled new alerts flag, or null to leave unchanged
     * @return the saved search
     * @throws NotFoundException  if the search does not exist
     * @throws ForbiddenException if the requesting user is not the owner
     */
    public SavedSearch update(String id, String userId, String name, Boolean alertsEnabled) {
        SavedSearch existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Saved search not found"));
        // Ownership check: only the owner may modify the search.
        if (!userId.equals(existing.getUserId())) {
            throw new ForbiddenException("Access denied.");
        }
        if (name != null && !name.isBlank()) {
            existing.setName(name.trim());
        }
        if (alertsEnabled != null) {
            existing.setAlertsEnabled(alertsEnabled);
        }
        return repository.save(existing);
    }

    /**
     * Deletes a saved search, enforcing owner access.
     *
     * @param id     id of the saved search
     * @param userId id of the requesting user (must own the search)
     * @throws NotFoundException  if the search does not exist
     * @throws ForbiddenException if the requesting user is not the owner
     */
    public void delete(String id, String userId) {
        SavedSearch existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Saved search not found"));
        // Ownership check before deletion.
        if (!userId.equals(existing.getUserId())) {
            throw new ForbiddenException("Access denied.");
        }
        repository.deleteById(id);
    }

    /**
     * After a new FoundItem is published, check all saved searches with alerts enabled.
     * For each matching saved search, dispatch a notification to its owner.
     */
    public void checkAlertsForNewItem(FoundItem item) {
        // Nothing to do without an item or a dispatcher to notify through.
        if (item == null || recoveryPulse == null) {
            return;
        }
        try {
            // Evaluate only searches that have alerting turned on.
            List<SavedSearch> alertSearches = repository.findByAlertsEnabled(true);
            for (SavedSearch search : alertSearches) {
                if (matchesFilters(item, search.getFilters())) {
                    notifyNewItemMatchesSavedSearch(search, item);
                }
            }
        } catch (RuntimeException ex) {
            // Alerting is best-effort; never let it break the item-publication flow.
            LOGGER.warn("Error checking saved search alerts: {}", ex.getMessage());
        }
    }

    /**
     * Decides whether a found item satisfies a saved search's filter criteria.
     *
     * <p>Empty/absent filters match everything. Otherwise all present criteria must pass (AND logic):
     * <ul>
     *   <li>{@code category}: case-insensitive exact match against the item category.</li>
     *   <li>{@code color}: the item color must contain the filter color (substring, normalized).</li>
     *   <li>{@code q}: free-text keyword that must appear in the item's title, description, brand, or
     *       any tag (substring, normalized).</li>
     * </ul>
     *
     * @param item    the candidate found item
     * @param filters the saved search's filter map
     * @return true if the item matches every present filter
     */
    private boolean matchesFilters(FoundItem item, Map<String, String> filters) {
        // No filters means an unconditional match.
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        // Category: must match exactly (case-insensitive) when specified.
        String cat = filters.get("category");
        if (cat != null && !cat.isBlank() && !cat.equalsIgnoreCase(item.getCategory())) {
            return false;
        }
        // Color: item color must contain the requested color substring when specified.
        String color = filters.get("color");
        if (color != null && !color.isBlank() && (item.getColor() == null || !normalize(item.getColor()).contains(normalize(color)))) {
            return false;
        }
        // Free-text keyword: require a hit in any of title/description/brand/tags.
        String q = filters.get("q");
        if (q != null && !q.isBlank()) {
            String keyword = normalize(q);
            boolean titleMatch = item.getTitle() != null && normalize(item.getTitle()).contains(keyword);
            boolean descMatch = item.getDescription() != null && normalize(item.getDescription()).contains(keyword);
            boolean brandMatch = item.getBrand() != null && normalize(item.getBrand()).contains(keyword);
            boolean tagMatch = item.getTags() != null && item.getTags().stream().anyMatch(t -> normalize(t).contains(keyword));
            // No field contained the keyword — not a match.
            if (!titleMatch && !descMatch && !brandMatch && !tagMatch) {
                return false;
            }
        }
        return true;
    }

    /**
     * Dispatches a "saved_search_match" notification to the saved search's owner, deep-linking to the
     * matching item and carrying the search id/name and the item id/title in the event context.
     *
     * @param search the matched saved search (its owner is the recipient)
     * @param item   the found item that matched
     */
    private void notifyNewItemMatchesSavedSearch(SavedSearch search, FoundItem item) {
        recoveryPulse.dispatch(new RecoveryPulseEvent(
                "saved_search_match",
                "matches",
                search.getUserId(),
                item.getId(),
                "/items/" + item.getId(),
                false,
                Map.of(
                        "saved_search_id", search.getId(),
                        "saved_search_name", search.getName(),
                        "found_item_id", item.getId() == null ? "" : item.getId(),
                        "found_item_title", item.getTitle() == null ? "" : item.getTitle()
                )
        ));
    }

    /** Lowercases and trims a string for case-insensitive substring matching; null becomes empty. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
