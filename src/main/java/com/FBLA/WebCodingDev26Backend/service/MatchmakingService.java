package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingService.class);
    private static final int STRONG_MATCH_THRESHOLD = 80;
    private static final Set<String> MATCHABLE_FOUND_STATUSES = Set.of(ItemStatus.FOUND, "approved");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "at", "case", "for", "in", "is", "it", "lost", "missing", "of", "on", "the",
            "to", "was", "with"
    );

    private final LostReportRepository lostReports;
    private final FoundItemRepository foundItems;
    private final AiMatchClient aiMatchClient;
    private final ClockService clock;
    private final int maxCandidates;
    private final int minConfidence;
    private final RecoveryCaseService recoveryCaseService;
    private final RecoveryPulseDispatcher recoveryPulse;

    public MatchmakingService(
            LostReportRepository lostReports,
            FoundItemRepository foundItems,
            AiMatchClient aiMatchClient,
            ClockService clock,
            @Value("${app.ai.matchmaking.max-candidates:8}") int maxCandidates,
            @Value("${app.ai.matchmaking.min-confidence:35}") int minConfidence
    ) {
        this(lostReports, foundItems, aiMatchClient, clock, maxCandidates, minConfidence, null, null);
    }

    @Autowired
    public MatchmakingService(
            LostReportRepository lostReports,
            FoundItemRepository foundItems,
            AiMatchClient aiMatchClient,
            ClockService clock,
            @Value("${app.ai.matchmaking.max-candidates:8}") int maxCandidates,
            @Value("${app.ai.matchmaking.min-confidence:35}") int minConfidence,
            RecoveryCaseService recoveryCaseService,
            RecoveryPulseDispatcher recoveryPulse
    ) {
        this.lostReports = lostReports;
        this.foundItems = foundItems;
        this.aiMatchClient = aiMatchClient;
        this.clock = clock;
        this.maxCandidates = Math.max(1, maxCandidates);
        this.minConfidence = Math.max(0, Math.min(100, minConfidence));
        this.recoveryCaseService = recoveryCaseService;
        this.recoveryPulse = recoveryPulse;
    }

    public List<MatchSuggestion> getMatchesForLostReport(String lostReportId) {
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        return typedMatches(report);
    }

    /** Decisions an admin can record on a suggested match. */
    private static final Set<String> MATCH_DECISIONS = Set.of("confirmed", "rejected", "linked");

    private static boolean isDecided(String status) {
        if (status == null) {
            return false;
        }
        return MATCH_DECISIONS.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Records an admin decision (confirmed / rejected / linked) on a single match
     * of a lost report and persists it directly, without recomputing matches (a
     * recompute would reset every match back to "suggested").
     */
    public LostReport decideMatch(String lostReportId, String foundItemId, String decision) {
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        boolean matched = false;
        for (Object value : report.getMatchedItems()) {
            if (value instanceof MatchSuggestion suggestion && foundItemId.equals(suggestion.getFoundItemId())) {
                suggestion.setStatus(decision);
                suggestion.setUpdatedDate(clock.now());
                matched = true;
            } else if (value instanceof Map<?, ?> rawMatch) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) rawMatch;
                Object fid = map.containsKey("found_item_id") ? map.get("found_item_id") : map.get("foundItemId");
                if (foundItemId.equals(String.valueOf(fid))) {
                    map.put("status", decision);
                    matched = true;
                }
            }
        }
        if (!matched) {
            throw new NotFoundException("Match not found for this lost report.");
        }
        // "Link Items" confirms these are the same item — establish a real link by
        // pointing the found item at this lost report.
        if ("linked".equals(decision)) {
            foundItems.findById(foundItemId).ifPresent(item -> {
                item.setLinkedLostReportId(lostReportId);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            });
            // Linking starts the return flow: take the report out of the active
            // admin queue (as "in review"), notify the owner a match was found, and
            // start/advance the recovery case. It is NOT recovered yet — the report
            // only becomes "resolved" once the item is actually picked up/completed.
            report.setStatus("in_review");
            report.setUpdatedDate(clock.now());
            LostReport savedReport = lostReports.save(report);
            if (recoveryPulse != null) {
                try {
                    recoveryPulse.strongMatchAvailable(savedReport, Map.of("found_item_id", foundItemId));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Unable to notify owner of linked match for lost report {}: {}", lostReportId, exception.getMessage());
                }
            }
            if (recoveryCaseService != null) {
                try {
                    recoveryCaseService.onMatchLinked(savedReport, foundItemId);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Unable to advance recovery case for linked match on lost report {}: {}", lostReportId, exception.getMessage());
                }
            }
            return savedReport;
        }
        report.setUpdatedDate(clock.now());
        return lostReports.save(report);
    }

    /** Carries any prior admin decision onto a freshly recomputed match set. */
    private void preserveDecisions(List<MatchSuggestion> previous, List<MatchSuggestion> incoming) {
        Map<String, MatchSuggestion> decided = new LinkedHashMap<>();
        for (MatchSuggestion match : previous) {
            if (match.getFoundItemId() != null && isDecided(match.getStatus())) {
                decided.put(match.getFoundItemId(), match);
            }
        }
        if (decided.isEmpty()) {
            return;
        }
        Set<String> incomingIds = new LinkedHashSet<>();
        for (MatchSuggestion match : incoming) {
            incomingIds.add(match.getFoundItemId());
            MatchSuggestion prior = decided.get(match.getFoundItemId());
            if (prior != null) {
                match.setStatus(prior.getStatus());
            }
        }
        // Keep decided matches the recompute dropped so a decision is never lost.
        for (MatchSuggestion prior : decided.values()) {
            if (!incomingIds.contains(prior.getFoundItemId())) {
                incoming.add(prior);
            }
        }
    }

    public List<MatchSuggestion> refreshMatchesForLostReport(String lostReportId) {
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        List<MatchSuggestion> previousMatches = typedMatches(report);
        Set<String> previousStrongMatches = strongMatchIds(previousMatches);
        List<FoundItem> candidates = foundItems.findAll().stream()
                .filter(this::eligibleFoundItem)
                .toList();
        List<MatchSuggestion> matches = new ArrayList<>(buildMatches(report, candidates));
        preserveDecisions(previousMatches, matches);
        report.setMatchedItems(new ArrayList<>(matches));
        markMatchedIfActive(report, matches);
        report.setUpdatedDate(clock.now());
        lostReports.save(report);
        if (!matches.isEmpty() && recoveryCaseService != null) {
            recoveryCaseService.refreshForLostReport(lostReportId);
        }
        dispatchNewStrongMatches(report, matches, previousStrongMatches);
        return matches;
    }

    public List<MatchSuggestion> refreshMatchesForFoundItem(String foundItemId) {
        FoundItem item = foundItems.findById(foundItemId)
                .orElseThrow(() -> new NotFoundException("Found item not found"));
        if (!eligibleFoundItem(item)) {
            return List.of();
        }

        List<MatchSuggestion> impacted = new ArrayList<>();
        for (LostReport report : lostReports.findAll()) {
            if (!eligibleLostReport(report)) {
                continue;
            }
            Set<String> previousStrongMatches = strongMatchIds(typedMatches(report));
            List<MatchSuggestion> matches = new ArrayList<>(buildMatches(report, List.of(item)));
            if (item.getLinkedLostReportId() != null && item.getLinkedLostReportId().equals(report.getId())) {
                matches.add(finderResponseSuggestion(item));
            }
            if (!matches.isEmpty()) {
                mergeMatches(report, matches);
                markMatchedIfActive(report, matches);
                report.setUpdatedDate(clock.now());
                lostReports.save(report);
                if (recoveryCaseService != null) {
                    recoveryCaseService.refreshForLostReport(report.getId());
                }
                dispatchNewStrongMatches(report, matches, previousStrongMatches);
                impacted.addAll(matches);
            }
        }
        return impacted;
    }

    private void dispatchNewStrongMatches(LostReport report, List<MatchSuggestion> matches, Set<String> previousStrongMatches) {
        if (recoveryPulse == null || matches == null || matches.isEmpty()) {
            return;
        }
        matches.stream()
                .filter(this::isStrongMatch)
                .filter(match -> match.getFoundItemId() != null && !previousStrongMatches.contains(match.getFoundItemId()))
                .forEach(match -> {
                    try {
                        recoveryPulse.strongMatchAvailable(report, Map.of("found_item_id", match.getFoundItemId()));
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Unable to dispatch strong match notification for lost report {}: {}", report.getId(), exception.getMessage());
                    }
                });
    }

    private Set<String> strongMatchIds(List<MatchSuggestion> matches) {
        Set<String> ids = new LinkedHashSet<>();
        for (MatchSuggestion match : matches) {
            if (isStrongMatch(match) && match.getFoundItemId() != null && !match.getFoundItemId().isBlank()) {
                ids.add(match.getFoundItemId());
            }
        }
        return ids;
    }

    private boolean isStrongMatch(MatchSuggestion match) {
        return match != null && match.getConfidence() != null && match.getConfidence() >= STRONG_MATCH_THRESHOLD;
    }

    private List<MatchSuggestion> buildMatches(LostReport report, List<FoundItem> possibleItems) {
        List<ScoredItem> scoredItems = possibleItems.stream()
                .map(item -> score(report, item))
                .filter(scored -> scored.score() >= minConfidence)
                .sorted(Comparator.comparingInt((ScoredItem scored) -> scored.score()).reversed())
                .limit(maxCandidates)
                .toList();

        if (scoredItems.isEmpty()) {
            return List.of();
        }

        Map<String, AiMatchClient.AiMatchResult> aiResults = new LinkedHashMap<>();
        aiMatchClient.rankMatches(report, scoredItems.stream().map(scored -> scored.item()).toList())
                .forEach(result -> aiResults.put(result.foundItemId(), result));

        List<MatchSuggestion> matches = new ArrayList<>();
        for (ScoredItem scored : scoredItems) {
            AiMatchClient.AiMatchResult aiResult = aiResults.get(scored.item().getId());
            matches.add(toSuggestion(scored, aiResult));
        }

        return matches.stream()
                .sorted(Comparator.comparing((MatchSuggestion match) -> match.getConfidence(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private MatchSuggestion toSuggestion(ScoredItem scored, AiMatchClient.AiMatchResult aiResult) {
        FoundItem item = scored.item();
        String now = clock.now();
        MatchSuggestion suggestion = new MatchSuggestion();
        suggestion.setFoundItemId(item.getId());
        suggestion.setFoundItemTitle(item.getTitle());
        suggestion.setCategory(item.getCategory());
        suggestion.setColor(item.getColor());
        suggestion.setBrand(item.getBrand());
        suggestion.setLocationFound(item.getLocationFound());
        suggestion.setDateFound(item.getDateFound());
        suggestion.setPhotoUrls(item.getPhotoUrls());
        suggestion.setStatus("suggested");
        suggestion.setCreatedDate(now);
        suggestion.setUpdatedDate(now);

        if (aiResult == null) {
            suggestion.setConfidence(scored.score());
            suggestion.setReasons(scored.reasons());
            suggestion.setSource("deterministic");
            return suggestion;
        }

        suggestion.setConfidence(scored.score());
        suggestion.setReasons(mergedReasons(aiResult.reasons(), scored.reasons()));
        suggestion.setSource("ai_assisted");
        return suggestion;
    }

    private void mergeMatches(LostReport report, List<MatchSuggestion> incomingMatches) {
        Map<String, MatchSuggestion> merged = new LinkedHashMap<>();
        for (MatchSuggestion existing : typedMatches(report)) {
            if (existing.getFoundItemId() != null && !existing.getFoundItemId().isBlank()) {
                merged.put(existing.getFoundItemId(), existing);
            }
        }
        incomingMatches.forEach(match -> {
            MatchSuggestion existing = merged.get(match.getFoundItemId());
            if (existing != null && isDecided(existing.getStatus())) {
                match.setStatus(existing.getStatus());
            }
            merged.put(match.getFoundItemId(), match);
        });
        report.setMatchedItems(new ArrayList<>(merged.values()));
    }

    private MatchSuggestion finderResponseSuggestion(FoundItem item) {
        String now = clock.now();
        MatchSuggestion suggestion = new MatchSuggestion();
        suggestion.setFoundItemId(item.getId());
        suggestion.setFoundItemTitle(item.getTitle());
        suggestion.setCategory(item.getCategory());
        suggestion.setColor(item.getColor());
        suggestion.setBrand(item.getBrand());
        suggestion.setLocationFound(item.getLocationFound());
        suggestion.setDateFound(item.getDateFound());
        suggestion.setPhotoUrls(item.getPhotoUrls());
        suggestion.setConfidence(100);
        suggestion.setReasons(List.of("finder response"));
        suggestion.setSource("finder_response");
        suggestion.setStatus("suggested");
        suggestion.setCreatedDate(now);
        suggestion.setUpdatedDate(now);
        return suggestion;
    }

    private void markMatchedIfActive(LostReport report, List<MatchSuggestion> matches) {
        String status = normalize(report.getStatus());
        if (!matches.isEmpty() && !status.equals("resolved") && !status.equals("closed")) {
            report.setStatus("matched");
        }
    }

    private List<MatchSuggestion> typedMatches(LostReport report) {
        if (report.getMatchedItems() == null) {
            return List.of();
        }
        List<MatchSuggestion> matches = new ArrayList<>();
        for (Object value : report.getMatchedItems()) {
            if (value instanceof MatchSuggestion suggestion) {
                matches.add(suggestion);
            } else if (value instanceof Map<?, ?> rawMatch) {
                matches.add(matchFromMap(rawMatch));
            } else if (value instanceof String foundItemId && !foundItemId.isBlank()) {
                MatchSuggestion suggestion = new MatchSuggestion();
                suggestion.setFoundItemId(foundItemId);
                suggestion.setSource("legacy");
                suggestion.setStatus("suggested");
                matches.add(suggestion);
            }
        }
        return matches;
    }

    private MatchSuggestion matchFromMap(Map<?, ?> rawMatch) {
        MatchSuggestion suggestion = new MatchSuggestion();
        suggestion.setFoundItemId(stringValue(rawMatch, "found_item_id", "foundItemId"));
        suggestion.setFoundItemTitle(stringValue(rawMatch, "found_item_title", "foundItemTitle"));
        suggestion.setCategory(stringValue(rawMatch, "category"));
        suggestion.setColor(stringValue(rawMatch, "color"));
        suggestion.setBrand(stringValue(rawMatch, "brand"));
        suggestion.setLocationFound(stringValue(rawMatch, "location_found", "locationFound"));
        suggestion.setDateFound(stringValue(rawMatch, "date_found", "dateFound"));
        suggestion.setConfidence(intValue(rawMatch, "confidence"));
        suggestion.setReasons(stringList(rawMatch, "reasons"));
        suggestion.setSource(valueOrDefault(stringValue(rawMatch, "source"), "legacy"));
        suggestion.setStatus(valueOrDefault(stringValue(rawMatch, "status"), "suggested"));
        suggestion.setCreatedDate(stringValue(rawMatch, "created_date", "createdDate"));
        suggestion.setUpdatedDate(stringValue(rawMatch, "updated_date", "updatedDate"));
        suggestion.setPhotoUrls(stringList(rawMatch, "photo_urls", "photoUrls"));
        return suggestion;
    }

    private ScoredItem score(LostReport report, FoundItem item) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Explainable NLC demo algorithm:
        // 1. Award large points for stable facts students can describe: category, brand, and color.
        // 2. Add smaller points for fuzzy evidence: keyword overlap, similar campus location, close dates, and tags.
        // 3. Keep only candidates above the confidence threshold, then sort highest first for "Possible Matches."
        // The final decision still belongs to an admin after a private claim detail is reviewed.
        if (sameText(report.getCategory(), item.getCategory())) {
            score += 30;
            reasons.add("category match");
        }
        if (sameText(report.getBrand(), item.getBrand())) {
            score += 20;
            reasons.add("brand match");
        }
        if (sameText(report.getColor(), item.getColor())) {
            score += 15;
            reasons.add("color match");
        }

        int textOverlap = textOverlapScore(searchText(report), searchText(item));
        if (textOverlap > 0) {
            score += textOverlap;
            reasons.add("description keywords overlap");
        }

        int locationOverlap = textOverlapScore(report.getLocationLost(), item.getLocationFound());
        if (locationOverlap > 0) {
            score += Math.min(15, locationOverlap);
            reasons.add("location is similar");
        }

        int dateScore = dateScore(report.getDateLost(), item.getDateFound());
        if (dateScore > 0) {
            score += dateScore;
            reasons.add("dates are close");
        }

        int tagOverlap = tagOverlapScore(report, item);
        if (tagOverlap > 0) {
            score += tagOverlap;
            reasons.add("tags match item details");
        }

        return new ScoredItem(item, clamp(score), reasons);
    }

    private boolean eligibleFoundItem(FoundItem item) {
        String status = normalize(item.getStatus());
        return !Boolean.TRUE.equals(item.getRestrictedVisibility())
                && MATCHABLE_FOUND_STATUSES.contains(ItemStatus.canonical(status))
                && !ItemStatus.isUnavailableForMatching(status);
    }

    private boolean eligibleLostReport(LostReport report) {
        String status = normalize(report.getStatus());
        return status.isBlank() || status.equals("open") || status.equals("matched");
    }

    private String searchText(LostReport report) {
        return String.join(" ",
                safe(report.getTitle()),
                safe(report.getCategory()),
                safe(report.getDescription()),
                safe(report.getColor()),
                safe(report.getBrand()),
                safe(report.getExtraNotes())
        );
    }

    private String searchText(FoundItem item) {
        return String.join(" ",
                safe(item.getTitle()),
                safe(item.getCategory()),
                safe(item.getDescription()),
                safe(item.getAiDescription()),
                safe(item.getDistinguishingFeatures()),
                safe(item.getColor()),
                safe(item.getBrand()),
                item.getTags() == null ? "" : String.join(" ", item.getTags())
        );
    }

    private int textOverlapScore(String first, String second) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        firstWords.retainAll(secondWords);
        return Math.min(20, firstWords.size() * 5);
    }

    private int tagOverlapScore(LostReport report, FoundItem item) {
        if (item.getTags() == null || item.getTags().isEmpty()) {
            return 0;
        }
        Set<String> reportWords = words(searchText(report));
        long matches = item.getTags().stream()
                .flatMap(tag -> words(tag).stream())
                .filter(reportWords::contains)
                .count();
        return (int) Math.min(10, matches * 5);
    }

    private int dateScore(String lostDate, String foundDate) {
        try {
            LocalDate lost = LocalDate.parse(lostDate);
            LocalDate found = LocalDate.parse(foundDate);
            long days = Math.abs(ChronoUnit.DAYS.between(lost, found));
            if (days <= 1) {
                return 10;
            }
            if (days <= 3) {
                return 6;
            }
            if (days <= 7) {
                return 3;
            }
            return 0;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String part : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!part.isBlank() && !STOP_WORDS.contains(part)) {
                words.add(part);
            }
        }
        return words;
    }

    private List<String> mergedReasons(List<String> primary, List<String> fallback) {
        List<String> reasons = new ArrayList<>();
        if (primary != null) {
            primary.stream().filter(reason -> reason != null && !reason.isBlank()).forEach(reasons::add);
        }
        if (reasons.isEmpty()) {
            reasons.addAll(fallback);
        } else {
            fallback.stream()
                    .filter(reason -> reason != null && !reason.isBlank())
                    .filter(reason -> reasons.stream().noneMatch(existing -> existing.equalsIgnoreCase(reason)))
                    .limit(2)
                    .forEach(reasons::add);
        }
        return reasons;
    }

    private boolean sameText(String first, String second) {
        String normalizedFirst = normalize(first);
        return !normalizedFirst.isBlank() && normalizedFirst.equals(normalize(second));
    }

    private String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stringValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Integer intValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> stringList(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?> rawList) {
                return rawList.stream()
                        .map(String::valueOf)
                        .filter(text -> !text.isBlank())
                        .toList();
            }
        }
        return List.of();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private record ScoredItem(FoundItem item, int score, List<String> reasons) {
    }
}
