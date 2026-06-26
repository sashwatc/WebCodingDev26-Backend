package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.service.SavedSearchService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FoundItemService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FoundItemService.class);

    private final FoundItemRepository repository;
    private final PatchMapper mapper;
    private final ClockService clock;
    private final WorkflowService workflow;
    private final MatchmakingService matchmakingService;
    private final AssetRegistryRecordRepository assetRegistry;
    private final ClaimRepository claims;
    private final CustodyEventRepository custodyEvents;
    private final RecoveryCaseRepository recoveryCases;
    private final LostReportRepository lostReports;
    private final CustodyLedgerService custodyLedgerService;
    private final InputSanitizer sanitizer;
    private SavedSearchService savedSearchService;

    public FoundItemService(FoundItemRepository repository, PatchMapper mapper, ClockService clock) {
        this(repository, mapper, clock, null, null, null, null, null, null, null, null, new InputSanitizer());
    }

    public FoundItemService(FoundItemRepository repository, PatchMapper mapper, ClockService clock, WorkflowService workflow) {
        this(repository, mapper, clock, workflow, null, null, null, null, null, null, null, new InputSanitizer());
    }

    public FoundItemService(
            FoundItemRepository repository,
            PatchMapper mapper,
            ClockService clock,
            MatchmakingService matchmakingService,
            AssetRegistryRecordRepository assetRegistry,
            ClaimRepository claims,
            CustodyEventRepository custodyEvents,
            RecoveryCaseRepository recoveryCases,
            LostReportRepository lostReports,
            CustodyLedgerService custodyLedgerService
    ) {
        this(repository, mapper, clock, null, matchmakingService, assetRegistry, claims, custodyEvents, recoveryCases, lostReports, custodyLedgerService, new InputSanitizer());
    }

    @Autowired
    public FoundItemService(
            FoundItemRepository repository,
            PatchMapper mapper,
            ClockService clock,
            WorkflowService workflow,
            MatchmakingService matchmakingService,
            AssetRegistryRecordRepository assetRegistry,
            ClaimRepository claims,
            CustodyEventRepository custodyEvents,
            RecoveryCaseRepository recoveryCases,
            LostReportRepository lostReports,
            CustodyLedgerService custodyLedgerService,
            InputSanitizer sanitizer
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.workflow = workflow;
        this.matchmakingService = matchmakingService;
        this.assetRegistry = assetRegistry;
        this.claims = claims;
        this.custodyEvents = custodyEvents;
        this.recoveryCases = recoveryCases;
        this.lostReports = lostReports;
        this.custodyLedgerService = custodyLedgerService;
        this.sanitizer = sanitizer;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSavedSearchService(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    public List<PublicFoundItemResponse> list() {
        return repository.findAll().stream()
                .filter(this::isPubliclyVisible)
                .map(PublicFoundItemResponse::from)
                .toList();
    }

    public Map<String, Object> listFiltered(
            String q, String category, String color, String location,
            String status, String dateFrom, String dateTo,
            String sortBy, int page, int size) {

        // Default: show publicly visible items (FOUND + CLAIM_PENDING + approved)
        String effectiveStatus = (status == null || status.isBlank()) ? "public" : status;
        List<FoundItem> all = repository.findAll();

        List<PublicFoundItemResponse> filtered = all.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getRestrictedVisibility()))
                .filter(item -> {
                    if ("all".equalsIgnoreCase(effectiveStatus)) return true;
                    if ("public".equals(effectiveStatus)) return ItemStatus.isPubliclyVisible(item.getStatus());
                    return effectiveStatus.equalsIgnoreCase(item.getStatus());
                })
                .filter(item -> category == null || category.isBlank() || category.equalsIgnoreCase(item.getCategory()))
                .filter(item -> color == null || color.isBlank() || normalize(item.getColor()).contains(normalize(color)))
                .filter(item -> location == null || location.isBlank() || normalize(item.getLocationFound()).contains(normalize(location)))
                .filter(item -> dateFrom == null || dateFrom.isBlank() || item.getDateFound() != null && item.getDateFound().compareTo(dateFrom) >= 0)
                .filter(item -> dateTo == null || dateTo.isBlank() || item.getDateFound() != null && item.getDateFound().compareTo(dateTo) <= 0)
                .filter(item -> matchesKeyword(item, q))
                .sorted(comparatorFor(sortBy, q))
                .map(PublicFoundItemResponse::from)
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<PublicFoundItemResponse> pageItems = filtered.subList(fromIndex, toIndex);

        return Map.of("items", pageItems, "total", total, "page", page, "size", size);
    }

    private boolean matchesKeyword(FoundItem item, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String keyword = normalize(q);
        return normalize(item.getTitle()).contains(keyword)
                || normalize(item.getDescription()).contains(keyword)
                || normalize(item.getBrand()).contains(keyword)
                || (item.getTags() != null && item.getTags().stream().anyMatch(t -> normalize(t).contains(keyword)));
    }

    private java.util.Comparator<FoundItem> comparatorFor(String sortBy, String q) {
        if ("relevance".equalsIgnoreCase(sortBy) && q != null && !q.isBlank()) {
            String keyword = normalize(q);
            return java.util.Comparator.comparingInt((FoundItem item) ->
                    normalize(item.getTitle()).contains(keyword) ? 0 : 1);
        }
        if ("recently_updated".equalsIgnoreCase(sortBy)) {
            return java.util.Comparator.comparing(
                    (FoundItem item) -> item.getUpdatedDate() == null ? "" : item.getUpdatedDate(),
                    java.util.Comparator.reverseOrder());
        }
        // Default: newest (by createdDate desc)
        return java.util.Comparator.comparing(
                (FoundItem item) -> item.getCreatedDate() == null ? "" : item.getCreatedDate(),
                java.util.Comparator.reverseOrder());
    }

    public PublicFoundItemResponse getPublic(String id) {
        FoundItem item = repository.findById(id).orElseThrow(() -> new NotFoundException("Found item not found"));
        if (!isPubliclyVisible(item)) {
            throw new NotFoundException("Found item not found");
        }
        return PublicFoundItemResponse.from(item);
    }

    public List<FoundItem> listAdmin() {
        return repository.findAll();
    }

    public FoundItem create(Map<String, Object> data) {
        return create(data, false);
    }

    public FoundItem create(Map<String, Object> data, boolean isStaff) {
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        if (!isStaff) {
            sanitizedData.remove("storageLocation");
            sanitizedData.remove("storage_location");
            sanitizedData.remove("privateVerificationClues");
            sanitizedData.remove("private_verification_clues");
        }
        FoundItem item = mapper.convert(sanitizedData, FoundItem.class);
        validateFoundItem(item);
        String now = clock.now();
        item.setId(valueOrGenerated(item.getId(), "found"));
        item.setCreatedDate(valueOrDefault(item.getCreatedDate(), now));
        item.setUpdatedDate(valueOrDefault(item.getUpdatedDate(), now));
        item.setStatus(valueOrDefault(item.getStatus(), ItemStatus.FOUND));
        item.setRecordType(valueOrDefault(item.getRecordType(), "found"));
        item.setIsFlagged(Boolean.TRUE.equals(item.getIsFlagged()));
        item.setClaimConfirmed(Boolean.TRUE.equals(item.getClaimConfirmed()));
        applyAssetRegistryDefaults(item);
        FoundItem savedItem = repository.save(item);
        appendCustody(savedItem, "intake_created", "Found item intake created.");
        refreshMatches(savedItem);
        if (savedSearchService != null && ItemStatus.isPubliclyVisible(savedItem.getStatus())) {
            savedSearchService.checkAlertsForNewItem(savedItem);
        }
        return savedItem;
    }

    public FoundItem update(String id, Map<String, Object> data) {
        return update(id, data, false);
    }

    public FoundItem update(String id, Map<String, Object> data, boolean isStaff) {
        FoundItem existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Found item not found"));
        Map<String, Object> sanitizedData = sanitizer.sanitizeMap(data);
        if (!isStaff) {
            sanitizedData.remove("storageLocation");
            sanitizedData.remove("storage_location");
            sanitizedData.remove("privateVerificationClues");
            sanitizedData.remove("private_verification_clues");
        }

        if (sanitizedData.containsKey("upsertRating") || sanitizedData.containsKey("upsert_rating")) {
            return upsertRating(existing, sanitizedData.getOrDefault("upsertRating", sanitizedData.get("upsert_rating")));
        }

        if (sanitizedData.containsKey("removeRatingByClaimId") || sanitizedData.containsKey("remove_rating_by_claim_id")) {
            String claimId = String.valueOf(sanitizedData.getOrDefault("removeRatingByClaimId", sanitizedData.get("remove_rating_by_claim_id")));
            existing.getRatings().removeIf(rating -> claimId.equals(rating.getClaimId()));
            existing.setUpdatedDate(clock.now());
            return repository.save(existing);
        }

        FoundItem patch = mapper.convert(sanitizedData, FoundItem.class);
        mapper.copyPresent(sanitizedData, patch, existing, "id", "createdDate");
        validateFoundItem(existing);
        applyAssetRegistryDefaults(existing);
        existing.setUpdatedDate(clock.now());
        FoundItem savedItem = repository.save(existing);
        refreshMatches(savedItem);
        return savedItem;
    }

    public Map<String, Object> delete(String id) {
        FoundItem existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Found item not found"));
        if (hasFoundItemReferences(id)) {
            existing.setStatus(ItemStatus.ARCHIVED);
            existing.setUpdatedDate(clock.now());
            FoundItem archivedItem = repository.save(existing);
            appendCustody(archivedItem, "archived", "Found item archived instead of hard-deleted because related records exist.");
            return Map.of("success", true, "archived", true, "item", archivedItem);
        }
        repository.deleteById(id);
        return Map.of("success", true, "archived", false);
    }

    public boolean isPubliclyVisible(FoundItem item) {
        return !Boolean.TRUE.equals(item.getRestrictedVisibility())
                && ItemStatus.isPubliclyVisible(item.getStatus());
    }

    private void validateFoundItem(FoundItem item) {
        if (item == null) {
            throw new BadRequestException("Found item payload is required");
        }
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new BadRequestException("Item title is required");
        }
        if (item.getCategory() == null || item.getCategory().isBlank()) {
            throw new BadRequestException("Category is required");
        }
        if (item.getLocationFound() == null || item.getLocationFound().isBlank()) {
            throw new BadRequestException("Location found is required");
        }
        if (item.getDateFound() == null || item.getDateFound().isBlank()) {
            throw new BadRequestException("Date found is required");
        }
        try {
            LocalDate.parse(item.getDateFound());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Date found must use YYYY-MM-DD format");
        }
    }

    private FoundItem upsertRating(FoundItem existing, Object ratingValue) {
        Rating incoming = mapper.convert(asMap(ratingValue), Rating.class);
        List<Rating> ratings = existing.getRatings() == null ? new ArrayList<>() : existing.getRatings();
        ratings.removeIf(rating -> incoming.getClaimId() != null && incoming.getClaimId().equals(rating.getClaimId()));
        ratings.add(incoming);
        existing.setRatings(ratings);
        existing.setUpdatedDate(clock.now());
        return repository.save(existing);
    }

    private void refreshMatches(FoundItem item) {
        if (matchmakingService == null || item == null || item.getId() == null || item.getId().isBlank()) {
            return;
        }

        try {
            matchmakingService.refreshMatchesForFoundItem(item.getId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to refresh matches for found item {}: {}", item.getId(), exception.getMessage());
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
        return normalized;
    }

    private String valueOrGenerated(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void applyAssetRegistryDefaults(FoundItem item) {
        if (assetRegistry == null || item.getAssetTag() == null || item.getAssetTag().isBlank()) {
            item.setRestrictedVisibility(Boolean.TRUE.equals(item.getRestrictedVisibility()));
            return;
        }

        assetRegistry.findByAssetTagIgnoreCase(item.getAssetTag().trim()).ifPresent(record -> {
            item.setRestrictedVisibility(true);
            item.setAssetRecordId(record.getId());
            item.setDepartmentDestination(record.getDepartmentDestination());
        });
    }

    private boolean hasFoundItemReferences(String foundItemId) {
        if (workflow != null && workflow.hasFoundItemReferences(foundItemId)) {
            return true;
        }
        return shouldArchiveInsteadOfDelete(foundItemId);
    }

    private boolean shouldArchiveInsteadOfDelete(String foundItemId) {
        if (claims != null && !claims.findByFoundItemId(foundItemId).isEmpty()) {
            return true;
        }
        if (custodyEvents != null && !custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId).isEmpty()) {
            return true;
        }
        if (recoveryCases != null && recoveryCases.findAll().stream().anyMatch(recoveryCase -> foundItemId.equals(recoveryCase.getSelectedFoundItemId()))) {
            return true;
        }
        if (lostReports != null && lostReports.findAll().stream().anyMatch(report -> containsMatch(report, foundItemId))) {
            return true;
        }
        return false;
    }

    private boolean containsMatch(LostReport report, String foundItemId) {
        if (report.getMatchedItems() == null) {
            return false;
        }
        return report.getMatchedItems().stream().anyMatch(match -> {
            if (match instanceof String id) {
                return foundItemId.equals(id);
            }
            if (match instanceof Map<?, ?> raw) {
                Object camel = raw.get("foundItemId");
                Object snake = raw.get("found_item_id");
                return foundItemId.equals(String.valueOf(camel)) || foundItemId.equals(String.valueOf(snake));
            }
            return false;
        });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void appendCustody(FoundItem item, String eventType, String notes) {
        if (custodyLedgerService == null) {
            return;
        }
        try {
            custodyLedgerService.appendEvent(
                    item.getId(),
                    eventType,
                    valueOrDefault(item.getFinderEmail(), "system@pvhs.demo"),
                    valueOrDefault(item.getFinderRole(), "system"),
                    item.getStorageLocation(),
                    notes,
                    null
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to append custody event for {}: {}", item.getId(), exception.getMessage());
        }
    }
}
