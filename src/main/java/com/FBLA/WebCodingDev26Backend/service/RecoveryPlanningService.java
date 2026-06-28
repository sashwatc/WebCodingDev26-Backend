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

/**
 * Deterministic recommendation engine that ranks which campus zones staff should search to recover
 * a lost item. There is no machine learning here — it is an explainable, rule-based scorer so every
 * recommendation can be justified with concrete reasons.
 *
 * <p>Responsibility: given a {@link LostReport}, score each {@link CampusZone} by combining signals
 * (the report's own selected zone, last-seen-location keyword overlap, event-hub linkage, historical
 * found-item patterns, and seeded PVHS category heuristics) and return the top zones with their
 * scores and human-readable reasons.
 *
 * <p>Collaborators: {@link CampusZoneRepository} (candidate zones) and
 * {@link FoundItemRepository} (historical found-item data used for pattern matching).
 */
@Service
public class RecoveryPlanningService {
    /** Source of candidate campus zones to score. */
    private final CampusZoneRepository zones;
    /** Source of historical found items, used to detect "similar items found nearby" patterns. */
    private final FoundItemRepository foundItems;

    /**
     * Constructs the planner with its repositories.
     *
     * @param zones      campus zone repository
     * @param foundItems found item repository (historical data)
     */
    public RecoveryPlanningService(CampusZoneRepository zones, FoundItemRepository foundItems) {
        this.zones = zones;
        this.foundItems = foundItems;
    }

    /**
     * Scores every campus zone for the given lost report and returns the strongest recommendations.
     *
     * <p>For each zone, starting from a baseline score of 8 and building a list of reasons:
     * <ul>
     *   <li>+42 if the report's selected campus zone id equals this zone.</li>
     *   <li>+32 if the report's free-text last-seen location shares keywords with this zone's
     *       label/description.</li>
     *   <li>+10 if the report is tied to an event hub (active event workflow).</li>
     *   <li>+8 per historical found item that was located in/near this zone AND matches the report's
     *       category or color, capped at +24.</li>
     *   <li>+14 if a seeded PVHS category-to-location heuristic applies.</li>
     * </ul>
     * Zones scoring at least 20 become recommendations (score capped at 95, reasons de-duplicated).
     * The list is then sorted by score descending and limited to the top 5.
     *
     * @param report the lost report to plan for
     * @return up to five ranked zone recommendations
     */
    public List<ZoneRecommendation> recommendZones(LostReport report) {
        List<CampusZone> availableZones = zones.findAll();
        List<FoundItem> historicalItems = foundItems.findAll();
        List<ZoneRecommendation> recommendations = new ArrayList<>();

        for (CampusZone zone : availableZones) {
            // Baseline score every zone starts with before signals are applied.
            int score = 8;
            List<String> reasons = new ArrayList<>();
            // Normalized searchable text for the zone (label + description, lowercased).
            String zoneText = normalize(zone.getLabel() + " " + zone.getDescription());

            // Signal 1: the reporter explicitly selected this zone.
            if (!safe(report.getCampusZoneId()).isBlank() && report.getCampusZoneId().equals(zone.getId())) {
                score += 42;
                reasons.add("Selected campus zone matches the report.");
            }
            // Signal 2: keyword overlap between the free-text last-seen location and the zone text.
            if (!safe(report.getLocationLost()).isBlank() && overlaps(report.getLocationLost(), zoneText)) {
                score += 32;
                reasons.add("Last seen location is near this zone.");
            }
            // Signal 3: the report is part of an active event workflow.
            if (!safe(report.getEventHubId()).isBlank()) {
                score += 10;
                reasons.add("Report is connected to an active event workflow.");
            }

            // Signal 4: count historical found items located in/near this zone that also match the
            // lost item's category or color — evidence this is a hotspot for similar items.
            long similarHistorical = historicalItems.stream()
                    .filter(item -> zone.getId().equals(item.getCampusZoneId()) || overlaps(item.getLocationFound(), zoneText))
                    .filter(item -> same(report.getCategory(), item.getCategory()) || same(report.getColor(), item.getColor()))
                    .count();
            if (similarHistorical > 0) {
                // 8 points per similar item, capped at 24.
                score += (int) Math.min(24, similarHistorical * 8);
                reasons.add("Similar items were historically found nearby.");
            }

            // Signal 5: hand-tuned PVHS heuristics linking item categories to typical loss locations.
            if (isSeededZonePattern(report, zone)) {
                score += 14;
                reasons.add("Seeded PVHS pattern suggests staff should check this location.");
            }

            // Only keep zones that cleared the relevance threshold; cap displayed score at 95.
            if (score >= 20) {
                recommendations.add(new ZoneRecommendation(zone.getId(), zone.getLabel(), Math.min(95, score), dedupe(reasons)));
            }
        }

        // Highest scoring zones first, at most five.
        return recommendations.stream()
                .sorted(Comparator.comparingInt((ZoneRecommendation recommendation) -> recommendation.score()).reversed())
                .limit(5)
                .toList();
    }

    /**
     * Encodes seeded PVHS domain heuristics that pair an item category with likely loss locations:
     * electronics near the gym/library, bags near the bus/cafeteria, and clothing near the
     * gym/auditorium.
     *
     * @return true if the report's category and the zone's label match one of the seeded patterns
     */
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

    /** Case-insensitive equality for two non-blank strings; returns false if the first is blank. */
    private boolean same(String first, String second) {
        String normalized = normalize(first);
        return !normalized.isBlank() && normalized.equals(normalize(second));
    }

    /**
     * Returns true when the two strings share at least one significant word (length &gt; 2), used
     * as a lightweight fuzzy location/text match.
     */
    private boolean overlaps(String first, String second) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        // Intersect the word sets; any remaining element means they overlap.
        firstWords.retainAll(secondWords);
        return !firstWords.isEmpty();
    }

    /**
     * Tokenizes text into significant lowercase words: splits on non-alphanumeric runs and keeps
     * tokens longer than two characters, de-duplicated in encounter order.
     */
    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String part : normalize(value).split("[^a-z0-9]+")) {
            if (part.length() > 2) {
                words.add(part);
            }
        }
        return words;
    }

    /** Removes duplicate reasons while preserving their original order. */
    private List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    /** Lowercases a string for comparison/tokenization; null becomes empty. */
    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    /** Null-safe string accessor: converts null to an empty string. */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Immutable recommendation result for one zone: its id, display label, final score (0..95), and
     * the de-duplicated list of reasons explaining the score.
     */
    public record ZoneRecommendation(String campusZoneId, String zoneLabel, int score, List<String> reasons) {
    }
}
