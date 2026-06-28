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

/**
 * Heart of the lost-and-found matching engine: scores candidate found items against lost
 * reports, persists the resulting "Possible Matches" onto each report, and drives the downstream
 * recovery flow when an admin acts on a match.
 *
 * <p>Matching strategy (two layers):
 * <ol>
 *   <li><b>Deterministic, explainable scorer</b> ({@link #score}) — awards weighted points for
 *       category/brand/color equality plus fuzzy signals (keyword overlap, similar location,
 *       close dates, tag overlap), clamped to 0-100. Only candidates at/above {@code minConfidence}
 *       survive, sorted highest-first and capped at {@code maxCandidates}.</li>
 *   <li><b>Optional AI assist</b> ({@link AiMatchClient}) — re-explains the shortlist with model
 *       reasons; if the AI is unavailable the deterministic result stands.</li>
 * </ol>
 *
 * <p>Lifecycle/business rules: recompute always re-derives suggestions but never loses an admin
 * decision (confirmed/rejected/linked is preserved/re-attached). "Linking" a match establishes a
 * real owner↔item link, moves the report to {@code in_review}, notifies the owner, and advances a
 * recovery case — but does NOT mark the item recovered (that only happens at pickup/completion).
 * Strong matches (confidence >= {@code STRONG_MATCH_THRESHOLD}) trigger owner notifications, fired
 * only for newly-strong items so owners aren't re-pinged.
 *
 * <p>Collaborators: {@link LostReportRepository}, {@link FoundItemRepository} (data),
 * {@link AiMatchClient} (AI ranking), {@link ClockService} (timestamps),
 * {@link RecoveryCaseService} (recovery workflow), {@link RecoveryPulseDispatcher} (notifications).
 */
@Service
public class MatchmakingService {
    /** Logger for non-fatal notification/recovery-case dispatch failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingService.class);
    /** Confidence at/above which a suggestion is a "strong match" worth notifying the owner about. */
    private static final int STRONG_MATCH_THRESHOLD = 80;
    /** Found-item statuses eligible to be matched (canonicalized FOUND or "approved"). */
    private static final Set<String> MATCHABLE_FOUND_STATUSES = Set.of(ItemStatus.FOUND, "approved");
    /** Common words ignored during keyword-overlap scoring to reduce noise. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "at", "case", "for", "in", "is", "it", "lost", "missing", "of", "on", "the",
            "to", "was", "with"
    );

    /** Lost-report data store (read reports, persist updated matches/status). */
    private final LostReportRepository lostReports;
    /** Found-item data store (read candidates, persist links). */
    private final FoundItemRepository foundItems;
    /** Optional AI ranker layered on top of deterministic scores. */
    private final AiMatchClient aiMatchClient;
    /** Clock abstraction for created/updated timestamps. */
    private final ClockService clock;
    /** Max number of scored candidates kept per report (>=1). */
    private final int maxCandidates;
    /** Minimum deterministic score required to be suggested (0-100). */
    private final int minConfidence;
    /** Recovery-case workflow service; advanced when matches link/refresh (may be null). */
    private final RecoveryCaseService recoveryCaseService;
    /** Notification dispatcher for strong/linked match events (may be null). */
    private final RecoveryPulseDispatcher recoveryPulse;

    /**
     * Lightweight constructor (tests): no recovery-case service and no notification dispatcher.
     */
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

    /**
     * Primary (Spring-injected) constructor. Clamps {@code maxCandidates} to >=1 and
     * {@code minConfidence} into 0-100.
     *
     * @param maxCandidates configured candidate cap ({@code app.ai.matchmaking.max-candidates})
     * @param minConfidence configured score floor ({@code app.ai.matchmaking.min-confidence})
     */
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

    /**
     * Returns the currently stored match suggestions for a lost report (read-only; no recompute).
     *
     * @param lostReportId the report id
     * @return its normalized {@link MatchSuggestion} list
     * @throws NotFoundException if the report does not exist
     */
    public List<MatchSuggestion> getMatchesForLostReport(String lostReportId) {
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        return typedMatches(report);
    }

    /** Decisions an admin can record on a suggested match. */
    private static final Set<String> MATCH_DECISIONS = Set.of("confirmed", "rejected", "linked");

    /** @return true when {@code status} is a recorded admin decision (confirmed/rejected/linked). */
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
        // Find the targeted match in the report's stored list (may be typed objects or raw maps)
        // and stamp the admin's decision onto it in place.
        for (Object value : report.getMatchedItems()) {
            if (value instanceof MatchSuggestion suggestion && foundItemId.equals(suggestion.getFoundItemId())) {
                suggestion.setStatus(decision);
                suggestion.setUpdatedDate(clock.now());
                matched = true;
            } else if (value instanceof Map<?, ?> rawMatch) {
                // Legacy/raw map entry: read the id under either naming and patch its status.
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) rawMatch;
                Object fid = map.containsKey("found_item_id") ? map.get("found_item_id") : map.get("foundItemId");
                if (foundItemId.equals(String.valueOf(fid))) {
                    map.put("status", decision);
                    matched = true;
                }
            }
        }
        // The decision must target a real suggestion on this report.
        if (!matched) {
            throw new NotFoundException("Match not found for this lost report.");
        }
        // "Link Items" confirms these are the same item — establish a real link by
        // pointing the found item at this lost report.
        if ("linked".equals(decision)) {
            // Point the found item at this lost report to record the confirmed link.
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
            // Notify the owner a confirmed match is available (best-effort).
            if (recoveryPulse != null) {
                try {
                    recoveryPulse.strongMatchAvailable(savedReport, Map.of("found_item_id", foundItemId));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Unable to notify owner of linked match for lost report {}: {}", lostReportId, exception.getMessage());
                }
            }
            // Advance the recovery case workflow for this link (best-effort).
            if (recoveryCaseService != null) {
                try {
                    recoveryCaseService.onMatchLinked(savedReport, foundItemId);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Unable to advance recovery case for linked match on lost report {}: {}", lostReportId, exception.getMessage());
                }
            }
            return savedReport;
        }
        // Non-linking decisions (confirmed/rejected) only need the report re-saved.
        report.setUpdatedDate(clock.now());
        return lostReports.save(report);
    }

    /**
     * Carries any prior admin decision onto a freshly recomputed match set.
     *
     * <p>For every previously-decided match (confirmed/rejected/linked): if the same found item
     * reappears in the recompute, its decision status is copied onto the new suggestion; if the
     * recompute dropped it, the prior decided match is re-appended so a decision is never silently
     * lost. Mutates {@code incoming} in place.
     *
     * @param previous prior match list (pre-recompute)
     * @param incoming freshly computed suggestions to reconcile
     */
    private void preserveDecisions(List<MatchSuggestion> previous, List<MatchSuggestion> incoming) {
        // Index prior decided matches by found-item id.
        Map<String, MatchSuggestion> decided = new LinkedHashMap<>();
        for (MatchSuggestion match : previous) {
            if (match.getFoundItemId() != null && isDecided(match.getStatus())) {
                decided.put(match.getFoundItemId(), match);
            }
        }
        if (decided.isEmpty()) {
            return;
        }
        // Re-apply each prior decision to the matching incoming suggestion.
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

    /**
     * Recomputes and persists the match suggestions for a single lost report.
     *
     * <p>Flow: snapshot prior matches and their strong-match ids; rescore against all eligible
     * found items; reconcile prior admin decisions ({@link #preserveDecisions}); replace the
     * report's match list; promote an active report to {@code matched} when it has suggestions;
     * save. If matches exist, refresh the recovery case, then notify the owner about any newly
     * strong matches.
     *
     * @param lostReportId the report to refresh
     * @return the recomputed (decision-preserved) match list
     * @throws NotFoundException if the report does not exist
     * @side-effect persists the report and may dispatch notifications / advance recovery cases
     */
    public List<MatchSuggestion> refreshMatchesForLostReport(String lostReportId) {
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        List<MatchSuggestion> previousMatches = typedMatches(report);
        Set<String> previousStrongMatches = strongMatchIds(previousMatches);
        // Only score found items currently eligible for matching.
        List<FoundItem> candidates = foundItems.findAll().stream()
                .filter(this::eligibleFoundItem)
                .toList();
        List<MatchSuggestion> matches = new ArrayList<>(buildMatches(report, candidates));
        // Don't clobber admin decisions when replacing the suggestion set.
        preserveDecisions(previousMatches, matches);
        report.setMatchedItems(new ArrayList<>(matches));
        markMatchedIfActive(report, matches);
        report.setUpdatedDate(clock.now());
        lostReports.save(report);
        if (!matches.isEmpty() && recoveryCaseService != null) {
            recoveryCaseService.refreshForLostReport(lostReportId);
        }
        // Fire owner notifications only for matches that became strong this round.
        dispatchNewStrongMatches(report, matches, previousStrongMatches);
        return matches;
    }

    /**
     * Re-evaluates one found item against every open lost report (used when an item is created/edited).
     *
     * <p>For each eligible lost report it scores the single item, and — if the item is explicitly
     * linked to that report ("I found this") — appends a guaranteed 100% finder-response suggestion.
     * New suggestions are merged into the report (preserving decided ones), the report is promoted
     * to {@code matched} if active, saved, its recovery case refreshed, and newly-strong matches
     * notified. Returns nothing for an ineligible item.
     *
     * @param foundItemId the found item to fan out
     * @return all suggestions touched across impacted reports
     * @throws NotFoundException if the found item does not exist
     */
    public List<MatchSuggestion> refreshMatchesForFoundItem(String foundItemId) {
        FoundItem item = foundItems.findById(foundItemId)
                .orElseThrow(() -> new NotFoundException("Found item not found"));
        // Skip items that aren't currently matchable (restricted/wrong status/etc.).
        if (!eligibleFoundItem(item)) {
            return List.of();
        }

        List<MatchSuggestion> impacted = new ArrayList<>();
        for (LostReport report : lostReports.findAll()) {
            // Only consider open/matched (active) reports.
            if (!eligibleLostReport(report)) {
                continue;
            }
            Set<String> previousStrongMatches = strongMatchIds(typedMatches(report));
            List<MatchSuggestion> matches = new ArrayList<>(buildMatches(report, List.of(item)));
            // A finder-submitted "I found this" item is an automatic 100% suggestion on its report.
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

    /**
     * Notifies the owner about strong matches that are newly strong this recompute.
     *
     * <p>No-op without a dispatcher or matches. Filters to strong matches whose found-item id was
     * not already strong (per {@code previousStrongMatches}) to avoid re-notifying; each dispatch
     * is best-effort and logs on failure.
     *
     * @param report                the lost report
     * @param matches               the current match suggestions
     * @param previousStrongMatches found-item ids that were already strong before this recompute
     */
    private void dispatchNewStrongMatches(LostReport report, List<MatchSuggestion> matches, Set<String> previousStrongMatches) {
        if (recoveryPulse == null || matches == null || matches.isEmpty()) {
            return;
        }
        matches.stream()
                .filter(this::isStrongMatch)
                // Only matches that weren't already strong (new strong matches).
                .filter(match -> match.getFoundItemId() != null && !previousStrongMatches.contains(match.getFoundItemId()))
                .forEach(match -> {
                    try {
                        recoveryPulse.strongMatchAvailable(report, Map.of("found_item_id", match.getFoundItemId()));
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Unable to dispatch strong match notification for lost report {}: {}", report.getId(), exception.getMessage());
                    }
                });
    }

    /** @return the set of found-item ids among {@code matches} that are strong matches. */
    private Set<String> strongMatchIds(List<MatchSuggestion> matches) {
        Set<String> ids = new LinkedHashSet<>();
        for (MatchSuggestion match : matches) {
            if (isStrongMatch(match) && match.getFoundItemId() != null && !match.getFoundItemId().isBlank()) {
                ids.add(match.getFoundItemId());
            }
        }
        return ids;
    }

    /** @return true when the match has a confidence at/above {@link #STRONG_MATCH_THRESHOLD}. */
    private boolean isStrongMatch(MatchSuggestion match) {
        return match != null && match.getConfidence() != null && match.getConfidence() >= STRONG_MATCH_THRESHOLD;
    }

    /**
     * Builds ranked match suggestions for a report from a pool of candidate found items.
     *
     * <p>Steps: deterministically score each candidate; keep those at/above {@code minConfidence};
     * sort by score descending and cap at {@code maxCandidates}. The surviving shortlist is sent to
     * the AI client for explanatory ranking (indexed by found-item id). Each scored item is turned
     * into a {@link MatchSuggestion} ({@code ai_assisted} when an AI result exists, else
     * {@code deterministic}), and the final list is sorted by confidence descending (nulls last).
     *
     * @param report       the lost report
     * @param possibleItems candidate found items to evaluate
     * @return ranked suggestions; empty when nothing clears the score floor
     */
    private List<MatchSuggestion> buildMatches(LostReport report, List<FoundItem> possibleItems) {
        // 1. Score, threshold, sort by score, and cap the candidate pool.
        List<ScoredItem> scoredItems = possibleItems.stream()
                .map(item -> score(report, item))
                .filter(scored -> scored.score() >= minConfidence)
                .sorted(Comparator.comparingInt((ScoredItem scored) -> scored.score()).reversed())
                .limit(maxCandidates)
                .toList();

        if (scoredItems.isEmpty()) {
            return List.of();
        }

        // 2. Ask the AI to (re-)explain the shortlist; index results by found-item id.
        Map<String, AiMatchClient.AiMatchResult> aiResults = new LinkedHashMap<>();
        aiMatchClient.rankMatches(report, scoredItems.stream().map(scored -> scored.item()).toList())
                .forEach(result -> aiResults.put(result.foundItemId(), result));

        // 3. Convert each scored item into a suggestion, merging any AI reasons.
        List<MatchSuggestion> matches = new ArrayList<>();
        for (ScoredItem scored : scoredItems) {
            AiMatchClient.AiMatchResult aiResult = aiResults.get(scored.item().getId());
            matches.add(toSuggestion(scored, aiResult));
        }

        // 4. Final ordering: highest confidence first.
        return matches.stream()
                .sorted(Comparator.comparing((MatchSuggestion match) -> match.getConfidence(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Converts a scored candidate into a persisted-shape {@link MatchSuggestion}, copying display
     * fields from the found item and stamping timestamps and {@code suggested} status.
     *
     * <p>Confidence always comes from the deterministic score. When an AI result is present the
     * source is {@code ai_assisted} and AI reasons are merged with the deterministic ones; otherwise
     * the source is {@code deterministic} with only the deterministic reasons.
     *
     * @param scored   the scored candidate
     * @param aiResult the AI ranking for this item, or {@code null}
     * @return the populated suggestion
     */
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

        // No AI input: pure deterministic suggestion.
        if (aiResult == null) {
            suggestion.setConfidence(scored.score());
            suggestion.setReasons(scored.reasons());
            suggestion.setSource("deterministic");
            return suggestion;
        }

        // AI-assisted: keep deterministic confidence but blend AI + deterministic reasons.
        suggestion.setConfidence(scored.score());
        suggestion.setReasons(mergedReasons(aiResult.reasons(), scored.reasons()));
        suggestion.setSource("ai_assisted");
        return suggestion;
    }

    /**
     * Merges newly-computed suggestions into a report's existing match list, keyed by found-item id.
     *
     * <p>Existing matches are indexed first; each incoming match overwrites/augments its entry, but
     * if the existing entry already carried an admin decision that decision is preserved on the
     * incoming match. The report's match list is replaced with the merged values.
     *
     * @param report          the report to update
     * @param incomingMatches the freshly computed suggestions to fold in
     */
    private void mergeMatches(LostReport report, List<MatchSuggestion> incomingMatches) {
        Map<String, MatchSuggestion> merged = new LinkedHashMap<>();
        // Seed with existing matches.
        for (MatchSuggestion existing : typedMatches(report)) {
            if (existing.getFoundItemId() != null && !existing.getFoundItemId().isBlank()) {
                merged.put(existing.getFoundItemId(), existing);
            }
        }
        // Overlay incoming matches, preserving any prior admin decision.
        incomingMatches.forEach(match -> {
            MatchSuggestion existing = merged.get(match.getFoundItemId());
            if (existing != null && isDecided(existing.getStatus())) {
                match.setStatus(existing.getStatus());
            }
            merged.put(match.getFoundItemId(), match);
        });
        report.setMatchedItems(new ArrayList<>(merged.values()));
    }

    /**
     * Builds a guaranteed 100%-confidence suggestion representing a finder's direct "I found this"
     * response to a specific lost report (source {@code finder_response}).
     *
     * @param item the finder-submitted found item linked to the report
     * @return the finder-response suggestion
     */
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

    /**
     * Promotes an active report to {@code matched} once it has suggestions.
     *
     * <p>Does nothing for empty match lists or terminal reports ({@code resolved}/{@code closed}),
     * so a recompute never reopens a finished report.
     *
     * @param report  the report to possibly promote
     * @param matches its current suggestions
     */
    private void markMatchedIfActive(LostReport report, List<MatchSuggestion> matches) {
        String status = normalize(report.getStatus());
        if (!matches.isEmpty() && !status.equals("resolved") && !status.equals("closed")) {
            report.setStatus("matched");
        }
    }

    /**
     * Normalizes a report's raw {@code matchedItems} into typed {@link MatchSuggestion}s.
     *
     * <p>Handles the heterogeneous persisted shapes: already-typed suggestions pass through; raw
     * maps are converted via {@link #matchFromMap}; bare found-item id strings become minimal legacy
     * suggestions. Returns empty when there are no matches.
     *
     * @param report the report
     * @return its suggestions as a typed list
     */
    private List<MatchSuggestion> typedMatches(LostReport report) {
        if (report.getMatchedItems() == null) {
            return List.of();
        }
        List<MatchSuggestion> matches = new ArrayList<>();
        for (Object value : report.getMatchedItems()) {
            if (value instanceof MatchSuggestion suggestion) {
                // Already typed.
                matches.add(suggestion);
            } else if (value instanceof Map<?, ?> rawMatch) {
                // Persisted as a generic map (snake/camel fields).
                matches.add(matchFromMap(rawMatch));
            } else if (value instanceof String foundItemId && !foundItemId.isBlank()) {
                // Oldest legacy shape: just a found-item id string.
                MatchSuggestion suggestion = new MatchSuggestion();
                suggestion.setFoundItemId(foundItemId);
                suggestion.setSource("legacy");
                suggestion.setStatus("suggested");
                matches.add(suggestion);
            }
        }
        return matches;
    }

    /**
     * Reconstructs a {@link MatchSuggestion} from a raw persisted map, tolerating both snake_case
     * and camelCase keys and defaulting source/status when absent.
     *
     * @param rawMatch the stored map representation
     * @return the typed suggestion
     */
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

    /**
     * Deterministic, explainable scorer comparing a lost report to one found item.
     *
     * <p>Weighting: category match +30, brand +20, color +15 (exact normalized equality); plus
     * fuzzy signals — description keyword overlap (up to +20), location similarity (up to +15),
     * date proximity (+10/+6/+3 within 1/3/7 days), and tag overlap (up to +10). Each contributing
     * signal appends a human-readable reason. The total is clamped to 0-100.
     *
     * @param report the lost report
     * @param item   the candidate found item
     * @return a {@link ScoredItem} bundling the item, clamped score, and reasons
     */
    private ScoredItem score(LostReport report, FoundItem item) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Explainable NLC demo algorithm:
        // 1. Award large points for stable facts students can describe: category, brand, and color.
        // 2. Add smaller points for fuzzy evidence: keyword overlap, similar campus location, close dates, and tags.
        // 3. Keep only candidates above the confidence threshold, then sort highest first for "Possible Matches."
        // The final decision still belongs to an admin after a private claim detail is reviewed.
        // Strong, stable facts (high weight).
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

        // Fuzzy evidence: shared description keywords (capped at +20).
        int textOverlap = textOverlapScore(searchText(report), searchText(item));
        if (textOverlap > 0) {
            score += textOverlap;
            reasons.add("description keywords overlap");
        }

        // Fuzzy evidence: similar lost/found location (capped at +15).
        int locationOverlap = textOverlapScore(report.getLocationLost(), item.getLocationFound());
        if (locationOverlap > 0) {
            score += Math.min(15, locationOverlap);
            reasons.add("location is similar");
        }

        // Temporal proximity of lost vs found dates.
        int dateScore = dateScore(report.getDateLost(), item.getDateFound());
        if (dateScore > 0) {
            score += dateScore;
            reasons.add("dates are close");
        }

        // Found-item tags overlapping the report's text (capped at +10).
        int tagOverlap = tagOverlapScore(report, item);
        if (tagOverlap > 0) {
            score += tagOverlap;
            reasons.add("tags match item details");
        }

        return new ScoredItem(item, clamp(score), reasons);
    }

    /**
     * @return true when a found item is currently matchable: not restricted-visibility, in a
     *         matchable canonical status (FOUND/approved), and not otherwise unavailable for matching
     */
    private boolean eligibleFoundItem(FoundItem item) {
        String status = normalize(item.getStatus());
        return !Boolean.TRUE.equals(item.getRestrictedVisibility())
                && MATCHABLE_FOUND_STATUSES.contains(ItemStatus.canonical(status))
                && !ItemStatus.isUnavailableForMatching(status);
    }

    /** @return true when a lost report is active for matching (blank/open/matched status). */
    private boolean eligibleLostReport(LostReport report) {
        String status = normalize(report.getStatus());
        return status.isBlank() || status.equals("open") || status.equals("matched");
    }

    /** @return a single whitespace-joined searchable blob of the report's descriptive fields. */
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

    /** @return a single whitespace-joined searchable blob of the found item's descriptive fields and tags. */
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

    /**
     * Scores shared significant words between two texts: 5 points per common word, capped at 20.
     *
     * @return the overlap score (0-20)
     */
    private int textOverlapScore(String first, String second) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        firstWords.retainAll(secondWords); // intersection of significant words
        return Math.min(20, firstWords.size() * 5);
    }

    /**
     * Scores how many of the found item's tag words appear in the report's text: 5 each, capped at 10.
     *
     * @return the tag-overlap score (0-10); 0 when the item has no tags
     */
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

    /**
     * Scores temporal closeness between the lost and found dates.
     *
     * @return 10 within 1 day, 6 within 3, 3 within 7, else 0; 0 when either date is unparseable
     */
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

    /**
     * Tokenizes text into a set of significant lowercase words.
     *
     * <p>Splits on non-alphanumerics and drops blanks and {@link #STOP_WORDS}. Insertion order is
     * preserved (LinkedHashSet); duplicates collapse.
     *
     * @param value source text
     * @return the set of significant tokens
     */
    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String part : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!part.isBlank() && !STOP_WORDS.contains(part)) {
                words.add(part);
            }
        }
        return words;
    }

    /**
     * Merges reason lists for a suggestion, preferring AI ("primary") reasons.
     *
     * <p>If any non-blank primary reasons exist they lead; up to two non-duplicate fallback
     * (deterministic) reasons are appended for context. If there are no primary reasons, all
     * fallback reasons are used.
     *
     * @param primary  AI-supplied reasons (may be null)
     * @param fallback deterministic reasons
     * @return the merged reason list
     */
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

    /** @return true when both texts normalize to the same non-blank value (exact equality match). */
    private boolean sameText(String first, String second) {
        String normalizedFirst = normalize(first);
        return !normalizedFirst.isBlank() && normalizedFirst.equals(normalize(second));
    }

    /** @return a null-safe, trimmed, lowercased copy of {@code value}. */
    private String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    /** @return {@code value} when non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Reads the first present key from a raw map as a string.
     *
     * @param map  the source map
     * @param keys candidate keys tried in order (e.g. snake_case then camelCase)
     * @return the stringified value of the first present key, or "" if none present
     */
    private String stringValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    /**
     * Reads an integer value from a raw map, tolerating numeric or string encodings.
     *
     * @return the int value, or {@code null} when missing or unparseable
     */
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

    /**
     * Reads a list-of-strings from a raw map under the first present key, dropping blanks.
     *
     * @param map  the source map
     * @param keys candidate keys tried in order
     * @return the non-blank string elements, or an empty list when absent/not a list
     */
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

    /** @return {@code value} or "" when null (null-safe string). */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** @return {@code value} clamped to the inclusive 0-100 confidence range. */
    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Internal pairing of a candidate found item with its deterministic score and reasons. */
    private record ScoredItem(FoundItem item, int score, List<String> reasons) {
    }
}
