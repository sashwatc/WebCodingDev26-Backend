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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SavedSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SavedSearchService.class);

    private final SavedSearchRepository repository;
    private final RecoveryPulseDispatcher recoveryPulse;

    @Autowired
    public SavedSearchService(SavedSearchRepository repository, RecoveryPulseDispatcher recoveryPulse) {
        this.repository = repository;
        this.recoveryPulse = recoveryPulse;
    }

    public List<SavedSearch> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

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

    public SavedSearch update(String id, String userId, String name, Boolean alertsEnabled) {
        SavedSearch existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Saved search not found"));
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

    public void delete(String id, String userId) {
        SavedSearch existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Saved search not found"));
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
        if (item == null || recoveryPulse == null) {
            return;
        }
        try {
            List<SavedSearch> alertSearches = repository.findByAlertsEnabled(true);
            for (SavedSearch search : alertSearches) {
                if (matchesFilters(item, search.getFilters())) {
                    notifyNewItemMatchesSavedSearch(search, item);
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("Error checking saved search alerts: {}", ex.getMessage());
        }
    }

    private boolean matchesFilters(FoundItem item, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        String cat = filters.get("category");
        if (cat != null && !cat.isBlank() && !cat.equalsIgnoreCase(item.getCategory())) {
            return false;
        }
        String color = filters.get("color");
        if (color != null && !color.isBlank() && (item.getColor() == null || !normalize(item.getColor()).contains(normalize(color)))) {
            return false;
        }
        String q = filters.get("q");
        if (q != null && !q.isBlank()) {
            String keyword = normalize(q);
            boolean titleMatch = item.getTitle() != null && normalize(item.getTitle()).contains(keyword);
            boolean descMatch = item.getDescription() != null && normalize(item.getDescription()).contains(keyword);
            boolean brandMatch = item.getBrand() != null && normalize(item.getBrand()).contains(keyword);
            boolean tagMatch = item.getTags() != null && item.getTags().stream().anyMatch(t -> normalize(t).contains(keyword));
            if (!titleMatch && !descMatch && !brandMatch && !tagMatch) {
                return false;
            }
        }
        return true;
    }

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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
