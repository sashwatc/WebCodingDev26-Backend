package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.EventRecoveryHub;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.EventRecoveryHubRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventRecoveryService {
    private final CampusZoneRepository zones;
    private final EventRecoveryHubRepository hubs;
    private final FoundItemRepository foundItems;
    private final FoundItemService foundItemService;
    private final PatchMapper mapper;
    private final ClockService clock;

    public EventRecoveryService(
            CampusZoneRepository zones,
            EventRecoveryHubRepository hubs,
            FoundItemRepository foundItems,
            FoundItemService foundItemService,
            PatchMapper mapper,
            ClockService clock
    ) {
        this.zones = zones;
        this.hubs = hubs;
        this.foundItems = foundItems;
        this.foundItemService = foundItemService;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<CampusZone> listZones() {
        return zones.findAll();
    }

    public List<EventRecoveryHub> listPublicHubs() {
        return hubs.findByPublicEnabledTrue();
    }

    public EventRecoveryHub getPublicHub(String id) {
        EventRecoveryHub hub = hubs.findById(id).orElseThrow(() -> new NotFoundException("Event hub not found"));
        if (!Boolean.TRUE.equals(hub.getPublicEnabled())) {
            throw new NotFoundException("Event hub not found");
        }
        return hub;
    }

    public Map<String, Object> displayFeed(String id) {
        EventRecoveryHub hub = getPublicHub(id);
        if (!Boolean.TRUE.equals(hub.getDisplayEnabled())) {
            throw new NotFoundException("Display feed not available");
        }

        List<PublicFoundItemResponse> publicItems = foundItems.findByEventHubId(id).stream()
                .filter(foundItemService::isPubliclyVisible)
                .map(PublicFoundItemResponse::from)
                .toList();
        List<CampusZone> publicZones = zones.findAllById(hub.getCampusZoneIds());

        Map<String, Object> feed = new LinkedHashMap<>();
        feed.put("event_hub", hub);
        feed.put("zones", publicZones);
        feed.put("found_items", publicItems);
        feed.put("notice", "Demo integration-ready event workflow. This does not claim connection to a live PVHS calendar or school display system.");
        return feed;
    }

    public EventRecoveryHub createHub(Map<String, Object> data, AppUser admin) {
        EventRecoveryHub hub = mapper.convert(data, EventRecoveryHub.class);
        String now = clock.now();
        hub.setId(valueOrGenerated(hub.getId(), "hub"));
        hub.setTenantId(valueOrDefault(hub.getTenantId(), "pvhs"));
        hub.setStatus(valueOrDefault(hub.getStatus(), "upcoming"));
        hub.setPublicEnabled(Boolean.TRUE.equals(hub.getPublicEnabled()));
        hub.setDisplayEnabled(Boolean.TRUE.equals(hub.getDisplayEnabled()));
        hub.setCreatedBy(admin.getEmail());
        hub.setCreatedDate(valueOrDefault(hub.getCreatedDate(), now));
        hub.setUpdatedDate(valueOrDefault(hub.getUpdatedDate(), now));
        return hubs.save(hub);
    }

    public EventRecoveryHub updateHub(String id, Map<String, Object> data) {
        EventRecoveryHub existing = hubs.findById(id).orElseThrow(() -> new NotFoundException("Event hub not found"));
        EventRecoveryHub patch = mapper.convert(data, EventRecoveryHub.class);
        mapper.copyPresent(data, patch, existing, "id", "createdDate", "createdBy");
        existing.setUpdatedDate(clock.now());
        return hubs.save(existing);
    }

    public EventRecoveryHub activate(String id) {
        EventRecoveryHub hub = hubs.findById(id).orElseThrow(() -> new NotFoundException("Event hub not found"));
        hub.setStatus("active");
        hub.setPublicEnabled(true);
        hub.setUpdatedDate(clock.now());
        return hubs.save(hub);
    }

    public EventRecoveryHub close(String id) {
        EventRecoveryHub hub = hubs.findById(id).orElseThrow(() -> new NotFoundException("Event hub not found"));
        hub.setStatus("closed");
        hub.setUpdatedDate(clock.now());
        return hubs.save(hub);
    }

    private String valueOrGenerated(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
