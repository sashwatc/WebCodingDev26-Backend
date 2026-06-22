package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AssetRegistryRecord;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.AssetRegistryRecordRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
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
    private final MatchmakingService matchmakingService;
    private final AssetRegistryRecordRepository assetRegistry;
    private final ClaimRepository claims;
    private final CustodyEventRepository custodyEvents;
    private final RecoveryCaseRepository recoveryCases;
    private final LostReportRepository lostReports;
    private final CustodyLedgerService custodyLedgerService;

    public FoundItemService(FoundItemRepository repository, PatchMapper mapper, ClockService clock) {
        this(repository, mapper, clock, null, null, null, null, null, null, null);
    }

    @Autowired
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
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.matchmakingService = matchmakingService;
        this.assetRegistry = assetRegistry;
        this.claims = claims;
        this.custodyEvents = custodyEvents;
        this.recoveryCases = recoveryCases;
        this.lostReports = lostReports;
        this.custodyLedgerService = custodyLedgerService;
    }

    public List<PublicFoundItemResponse> list() {
        return repository.findAll().stream()
                .filter(this::isPubliclyVisible)
                .map(PublicFoundItemResponse::from)
                .toList();
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
        FoundItem item = mapper.convert(data, FoundItem.class);
        String now = clock.now();
        item.setId(valueOrGenerated(item.getId(), "found"));
        item.setCreatedDate(valueOrDefault(item.getCreatedDate(), now));
        item.setUpdatedDate(valueOrDefault(item.getUpdatedDate(), now));
        item.setStatus(valueOrDefault(item.getStatus(), "pending_review"));
        item.setRecordType(valueOrDefault(item.getRecordType(), "found"));
        item.setIsFlagged(Boolean.TRUE.equals(item.getIsFlagged()));
        item.setClaimConfirmed(Boolean.TRUE.equals(item.getClaimConfirmed()));
        applyAssetRegistryDefaults(item);
        FoundItem saved = repository.save(item);
        appendCustody(saved, "intake_created", "Found item intake created.");
        if (matchmakingService != null) {
            try {
                matchmakingService.refreshMatchesForFoundItem(saved.getId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Unable to refresh matches for found item {}: {}", saved.getId(), exception.getMessage());
            }
        }
        return saved;
    }

    public FoundItem update(String id, Map<String, Object> data) {
        FoundItem existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Found item not found"));

        if (data.containsKey("upsertRating") || data.containsKey("upsert_rating")) {
            return upsertRating(existing, data.getOrDefault("upsertRating", data.get("upsert_rating")));
        }

        if (data.containsKey("removeRatingByClaimId") || data.containsKey("remove_rating_by_claim_id")) {
            String claimId = String.valueOf(data.getOrDefault("removeRatingByClaimId", data.get("remove_rating_by_claim_id")));
            existing.getRatings().removeIf(rating -> claimId.equals(rating.getClaimId()));
            existing.setUpdatedDate(clock.now());
            return repository.save(existing);
        }

        FoundItem patch = mapper.convert(data, FoundItem.class);
        mapper.copyPresent(data, patch, existing, "id", "createdDate");
        applyAssetRegistryDefaults(existing);
        existing.setUpdatedDate(clock.now());
        return repository.save(existing);
    }

    public boolean delete(String id) {
        FoundItem existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Found item not found"));
        if (shouldArchiveInsteadOfDelete(id)) {
            existing.setStatus("archived");
            existing.setUpdatedDate(clock.now());
            repository.save(existing);
            appendCustody(existing, "archived", "Found item archived instead of hard-deleted because related records exist.");
            return true;
        }
        repository.deleteById(id);
        return true;
    }

    public boolean isPubliclyVisible(FoundItem item) {
        String status = normalize(item.getStatus());
        return !Boolean.TRUE.equals(item.getRestrictedVisibility())
                && status.equals("approved");
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
