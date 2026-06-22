package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.CampusZone;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.repository.CampusZoneRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RecoveryPlanningService {
    private final CampusZoneRepository zones;
    private final FoundItemRepository foundItems;

    public RecoveryPlanningService(CampusZoneRepository zones, FoundItemRepository foundItems) {
        this.zones = zones;
        this.foundItems = foundItems;
    }

    public List<ZoneRecommendation> recommendZones(LostReport report) {
        List<CampusZone> availableZones = zones.findAll();
        List<FoundItem> historicalItems = foundItems.findAll();
        List<ZoneRecommendation> recommendations = new ArrayList<>();

        for (CampusZone zone : availableZones) {
            int score = 8;
            List<String> reasons = new ArrayList<>();
            String zoneText = normalize(zone.getLabel() + " " + zone.getDescription());

            if (!safe(report.getCampusZoneId()).isBlank() && report.getCampusZoneId().equals(zone.getId())) {
                score += 42;
                reasons.add("Selected campus zone matches the report.");
            }
            if (!safe(report.getLocationLost()).isBlank() && overlaps(report.getLocationLost(), zoneText)) {
                score += 32;
                reasons.add("Last seen location is near this zone.");
            }
            if (!safe(report.getEventHubId()).isBlank()) {
                score += 10;
                reasons.add("Report is connected to an active event workflow.");
            }

            long similarHistorical = historicalItems.stream()
                    .filter(item -> zone.getId().equals(item.getCampusZoneId()) || overlaps(item.getLocationFound(), zoneText))
                    .filter(item -> same(report.getCategory(), item.getCategory()) || same(report.getColor(), item.getColor()))
                    .count();
            if (similarHistorical > 0) {
                score += (int) Math.min(24, similarHistorical * 8);
                reasons.add("Similar items were historically found nearby.");
            }

            if (isSeededZonePattern(report, zone)) {
                score += 14;
                reasons.add("Seeded PVHS pattern suggests staff should check this location.");
            }

            if (score >= 20) {
                recommendations.add(new ZoneRecommendation(zone.getId(), zone.getLabel(), Math.min(95, score), dedupe(reasons)));
            }
        }

        return recommendations.stream()
                .sorted(Comparator.comparingInt(ZoneRecommendation::score).reversed())
                .limit(5)
                .toList();
    }

    private boolean isSeededZonePattern(LostReport report, CampusZone zone) {
        String category = normalize(report.getCategory());
        String label = normalize(zone.getLabel());
        if (category.contains("electronics") && (label.contains("gym") || label.contains("library"))) {
            return true;
        }
        if (category.contains("bags") && (label.contains("bus") || label.contains("cafeteria"))) {
            return true;
        }
        return category.contains("clothing") && (label.contains("gym") || label.contains("auditorium"));
    }

    private boolean same(String first, String second) {
        String normalized = normalize(first);
        return !normalized.isBlank() && normalized.equals(normalize(second));
    }

    private boolean overlaps(String first, String second) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        firstWords.retainAll(secondWords);
        return !firstWords.isEmpty();
    }

    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String part : normalize(value).split("[^a-z0-9]+")) {
            if (part.length() > 2) {
                words.add(part);
            }
        }
        return words;
    }

    private List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ZoneRecommendation(String campusZoneId, String zoneLabel, int score, List<String> reasons) {
    }
}
