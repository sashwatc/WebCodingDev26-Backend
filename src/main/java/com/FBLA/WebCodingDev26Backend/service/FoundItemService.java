package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Rating;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoundItemService {
    private final FoundItemRepository repository;
    private final PatchMapper mapper;
    private final ClockService clock;

    public FoundItemService(FoundItemRepository repository, PatchMapper mapper, ClockService clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<FoundItem> list() {
        return repository.findAll();
    }

    @Transactional
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
        return repository.save(item);
    }

    @Transactional
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
        existing.setUpdatedDate(clock.now());
        return repository.save(existing);
    }

    @Transactional
    public boolean delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Found item not found");
        }
        repository.deleteById(id);
        return true;
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
}
