package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Core lost-and-found matching and claim-lifecycle engine.
 *
 * <p>Two broad responsibilities:
 * <ul>
 *   <li><b>Matching</b> — scores found items against lost reports using a weighted
 *       heuristic (category, color, brand, shared words, date proximity, location),
 *       merges/de-duplicates suggested matches onto each report, and flips a report
 *       to "matched" when candidates exist. Used both when a lost report is filed/edited
 *       ({@link #syncMatchesForLostReport}) and when a found item is filed/edited
 *       ({@link #syncMatchesForFoundItem}).</li>
 *   <li><b>Claim rules</b> — validates claim payloads and status transitions
 *       ({@link #validateClaim}) and applies the resulting side effects to the
 *       underlying found item's status ({@link #applyClaimStatusSideEffects}).</li>
 * </ul>
 *
 * <p>Collaborators: found-item, lost-report and claim repositories for persistence,
 * plus {@link ClockService} for timestamps. Matches are stored as loosely-typed
 * {@code Map<String,Object>} entries (JSON-friendly) on each lost report.
 */
@Service
public class WorkflowService {
    /** The complete set of valid claim status values used for payload validation. */
    private static final Set<String> CLAIM_STATUSES = Set.of(
            "submitted",
            "pending_review",
            "under_review",
            "need_more_info",
            "approved",
            "rejected",
            "completed"
    );
    /** Splits normalized text into word tokens on any run of non-alphanumeric chars. */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    /** Repository for found items — read for scoring, updated on claim side-effects. */
    private final FoundItemRepository foundItems;
    /** Repository for lost reports — where computed matches and "matched" status are saved. */
    private final LostReportRepository lostReports;
    /** Repository for claims — used to detect competing approved/completed claims. */
    private final ClaimRepository claims;
    /** Abstraction over "now" so timestamps are consistent and testable. */
    private final ClockService clock;

    /**
     * Constructs the workflow engine with its repositories and clock (injected by Spring).
     *
     * @param foundItems  found-item persistence
     * @param lostReports lost-report persistence
     * @param claims      claim persistence
     * @param clock       timestamp source
     */
    public WorkflowService(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            ClaimRepository claims,
            ClockService clock
    ) {
        this.foundItems = foundItems;
        this.lostReports = lostReports;
        this.claims = claims;
        this.clock = clock;
    }

    /**
     * Recomputes and stores the suggested found-item matches for a single lost report.
     *
     * <p>Scores every found item against the report, keeps the non-null (above-threshold)
     * matches, merges them with any existing matches on the report, and persists. If the
     * report ends up with at least one match and isn't already resolved/closed, its status
     * is advanced to "matched". The updated timestamp is always refreshed.
     *
     * @param report the lost report to (re)match; returned unchanged if null or id-less
     * @return the saved report (or the original when not eligible)
     */
    public LostReport syncMatchesForLostReport(LostReport report) {
        // Guard: need a persisted report to attach matches to.
        if (report == null || blank(report.getId())) {
            return report;
        }

        // Score this report against every found item, dropping below-threshold (null)
        // results, then merge the survivors with the report's existing matches.
        List<Map<String, Object>> matches = mergeMatches(report.getMatchedItems(), foundItems.findAll().stream()
                .map(item -> score(report, item))
                .filter(Objects::nonNull)
                .toList());

        report.setMatchedItems(new ArrayList<>(matches));
        // Promote to "matched" only when there are candidates and the report isn't
        // already in a terminal (resolved/closed) state.
        if (!matches.isEmpty() && !Set.of("resolved", "closed").contains(normalize(report.getStatus()))) {
            report.setStatus("matched");
        }
        report.setUpdatedDate(clock.now());
        return lostReports.save(report);
    }

    /**
     * Recomputes suggested matches across ALL lost reports for a newly filed/edited
     * found item, saving only the reports whose match set actually changed.
     *
     * <p>For each report it builds up to two candidate matches for this item:
     * <ul>
     *   <li>an explicit, confidence-100 "finder_response" match when the found item's
     *       {@code linkedLostReportId} points at that report (a confirmed link), and</li>
     *   <li>a heuristic "ai_suggestion" match from {@link #score} when above threshold.</li>
     * </ul>
     * These are merged with the report's existing matches; if the merged set is identical
     * to what's already stored ({@link #sameMatches}) the report is skipped, otherwise the
     * matches/status/timestamp are updated and saved.
     *
     * @param foundItem the found item to propagate matches for; no-op if null or id-less
     */
    public void syncMatchesForFoundItem(FoundItem foundItem) {
        // Guard: need a persisted found item to match against reports.
        if (foundItem == null || blank(foundItem.getId())) {
            return;
        }

        for (LostReport report : lostReports.findAll()) {
            // (1) Explicit link: a finder confirmed this item belongs to this report → max confidence.
            Map<String, Object> explicitMatch = null;
            if (!blank(report.getId()) && report.getId().equals(firstNonBlank(foundItem.getLinkedLostReportId()))) {
                explicitMatch = match(foundItem.getId(), 100, List.of("finder response"), "finder_response");
            }

            // (2) Heuristic score for this report/item pair (null when below threshold).
            Map<String, Object> aiMatch = score(report, foundItem);
            List<Map<String, Object>> nextMatches = new ArrayList<>();
            if (explicitMatch != null) {
                nextMatches.add(explicitMatch);
            }
            if (aiMatch != null) {
                nextMatches.add(aiMatch);
            }
            // Merge new candidates with whatever the report already had.
            List<Map<String, Object>> matches = mergeMatches(report.getMatchedItems(), nextMatches);

            // Skip the write entirely if nothing meaningful changed (avoids churn).
            if (sameMatches(report.getMatchedItems(), matches)) {
                continue;
            }

            report.setMatchedItems(new ArrayList<>(matches));
            // Same "matched" promotion rule as the per-report path.
            if (!matches.isEmpty() && !Set.of("resolved", "closed").contains(normalize(report.getStatus()))) {
                report.setStatus("matched");
            }
            report.setUpdatedDate(clock.now());
            lostReports.save(report);
        }
    }

    /**
     * Reports whether any other record still references a given found item — used to
     * guard deletion/archival.
     *
     * @param foundItemId the found item id to check
     * @return true if at least one claim references it, OR any lost report lists it among
     *         its matched items; false when the id is blank or unreferenced
     */
    public boolean hasFoundItemReferences(String foundItemId) {
        if (blank(foundItemId)) {
            return false;
        }

        // Referenced if a claim points at it, or any report's match list contains its id.
        return !claims.findByFoundItemId(foundItemId).isEmpty() || lostReports.findAll().stream()
                .anyMatch(report -> safeMatches(report).stream()
                        .anyMatch(match -> foundItemId.equals(matchFoundItemId(match))));
    }

    /**
     * Validates a claim payload and the legality of its status transition before it is
     * persisted.
     *
     * <p>Required-field checks: payload present, found-item id, claimant name/email, and
     * claim reason. The status (if set) must be one of {@link #CLAIM_STATUSES}. Then
     * business-rule checks:
     * <ul>
     *   <li>the referenced found item must exist;</li>
     *   <li>an archived found item can't take new claims unless the claim is being
     *       "completed" (the returning-item finalization);</li>
     *   <li>a brand-new claim ({@code previousClaim == null}) cannot start out approved
     *       or completed — it must be submitted first;</li>
     *   <li>"completed" is only reachable from a prior "approved" (or already "completed")
     *       state — i.e. after pickup confirmation;</li>
     *   <li>"approved" is rejected if another claim on the same item is already
     *       approved/completed (only one winner per item).</li>
     * </ul>
     *
     * @param claim         the incoming claim to validate
     * @param previousClaim the persisted prior version (null for a brand-new claim)
     * @throws BadRequestException on any field or transition rule violation
     * @throws NotFoundException   if the referenced found item does not exist
     */
    public void validateClaim(Claim claim, Claim previousClaim) {
        // --- Required field validation ---
        if (claim == null) {
            throw new BadRequestException("Claim payload is required");
        }
        if (blank(claim.getFoundItemId())) {
            throw new BadRequestException("found_item_id is required");
        }
        if (blank(claim.getClaimantName())) {
            throw new BadRequestException("Claimant name is required");
        }
        if (blank(claim.getClaimantEmail())) {
            throw new BadRequestException("Claimant email is required");
        }
        if (blank(claim.getClaimReason())) {
            throw new BadRequestException("Claim reason is required");
        }
        // Status, if provided, must be a recognized value.
        if (!blank(claim.getStatus()) && !CLAIM_STATUSES.contains(claim.getStatus())) {
            throw new BadRequestException("Invalid claim status: " + claim.getStatus());
        }

        // The claim must point at a real found item.
        FoundItem foundItem = foundItems.findById(claim.getFoundItemId())
                .orElseThrow(() -> new NotFoundException("Claim must reference an existing Found Item"));
        // Archived items are closed to new claim activity, except the final "completed" step.
        if (ItemStatus.isArchived(foundItem.getStatus()) && !"completed".equals(claim.getStatus())) {
            throw new BadRequestException("This Found Item is no longer available for claims");
        }
        // A new claim cannot be created already-approved/completed — it must be submitted first.
        if (previousClaim == null && Set.of("approved", "completed").contains(normalize(claim.getStatus()))) {
            throw new BadRequestException("New claims must be submitted before admin approval");
        }
        // "completed" is only legal as a transition out of "approved" (or staying "completed").
        if ("completed".equals(normalize(claim.getStatus()))) {
            String priorStatus = normalize(previousClaim == null ? null : previousClaim.getStatus());
            if (!"completed".equals(priorStatus) && !"approved".equals(priorStatus)) {
                throw new BadRequestException("A claim can only be completed after it is approved and the pickup is confirmed");
            }
        }
        // Enforce a single approved/completed claim per found item.
        if ("approved".equals(claim.getStatus())) {
            boolean alreadyApproved = claims.findByFoundItemId(claim.getFoundItemId()).stream()
                    .anyMatch(existingClaim -> !Objects.equals(existingClaim.getId(), claim.getId())
                            && Set.of("approved", "completed").contains(normalize(existingClaim.getStatus())));
            if (alreadyApproved) {
                throw new BadRequestException("This Found Item already has an approved claim");
            }
        }
    }

    /**
     * Propagates a claim's status change onto the underlying found item's status.
     *
     * <p>Only runs when the status actually changed. Transitions handled:
     * <ul>
     *   <li><b>approved</b> → mark the item VERIFIED (reserved for this claimant);</li>
     *   <li><b>completed</b> → mark the item "returned", flag claim-confirmed and stamp
     *       the confirmation time (using the claim's received-confirmed time, or now);</li>
     *   <li><b>rejected</b> (from a previously approved claim) → if no other claim is
     *       still approved/completed for the item, release it back to FOUND/available.</li>
     * </ul>
     * Each handled case saves the item with a refreshed updated timestamp.
     *
     * @param claim         the claim whose new status should drive item side-effects
     * @param previousClaim the prior version (null for new); used to detect change and rejection-from-approved
     */
    public void applyClaimStatusSideEffects(Claim claim, Claim previousClaim) {
        // No-op when there's no item to touch or the status didn't actually change.
        if (claim == null || blank(claim.getFoundItemId()) || Objects.equals(claim.getStatus(), previousClaim == null ? null : previousClaim.getStatus())) {
            return;
        }

        // Resolve the affected found item; nothing to do if it no longer exists.
        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElse(null);
        if (item == null) {
            return;
        }

        // Approved → item is now verified/reserved for this claimant.
        if ("approved".equals(claim.getStatus())) {
            item.setStatus(ItemStatus.VERIFIED);
            item.setUpdatedDate(clock.now());
            foundItems.save(item);
            return;
        }

        // Completed → pickup confirmed; record return + confirmation timestamp.
        if ("completed".equals(claim.getStatus())) {
            // Prefer the explicit received-confirmed time; fall back to now if absent.
            String completedAt = blank(claim.getReceivedConfirmedAt()) ? clock.now() : claim.getReceivedConfirmedAt();
            item.setStatus("returned");
            item.setClaimConfirmed(true);
            item.setClaimConfirmedAt(completedAt);
            item.setUpdatedDate(clock.now());
            foundItems.save(item);
            return;
        }

        // Rejected after having been approved → may need to release the item.
        if ("rejected".equals(claim.getStatus()) && previousClaim != null && "approved".equals(previousClaim.getStatus())) {
            // Only release if no OTHER claim still holds an approved/completed status on this item.
            boolean stillApproved = claims.findByFoundItemId(claim.getFoundItemId()).stream()
                    .anyMatch(existingClaim -> !Objects.equals(existingClaim.getId(), claim.getId())
                            && Set.of("approved", "completed").contains(normalize(existingClaim.getStatus())));
            if (!stillApproved) {
                // Return the item to the available "found" pool.
                item.setStatus(ItemStatus.FOUND);
                item.setUpdatedDate(clock.now());
                foundItems.save(item);
            }
        }
    }

    /**
     * Heuristically scores how well a found item matches a lost report and, when the
     * score clears the threshold, produces an "ai_suggestion" match entry.
     *
     * <p>Eligibility: the item must be a real, persisted FOUND item that is still
     * available — items that are actually lost-records, archived, claim-pending, or
     * already verified are excluded (return null).
     *
     * <p>Weighted signals (additive):
     * <ul>
     *   <li>same category: +25</li>
     *   <li>same color (when report specifies one): +15</li>
     *   <li>same brand (when report specifies one): +20</li>
     *   <li>overlapping descriptive words: +6 per shared token, capped at +25</li>
     *   <li>dates within 3 days of each other: +10</li>
     *   <li>overlapping/contained location strings: +10</li>
     * </ul>
     * Each contributing signal also appends a human-readable reason.
     *
     * @param report the lost report being matched
     * @param item   the candidate found item
     * @return a match map (confidence capped at 98) when total score &ge; 35, else null
     */
    private Map<String, Object> score(LostReport report, FoundItem item) {
        // Need both sides and a real item id to score.
        if (report == null || item == null || blank(item.getId())) {
            return null;
        }
        // Exclude items that aren't eligible to be matched/claimed: lost-type records,
        // archived items, items pending a claim, or items already verified/reserved.
        if ("lost".equals(normalize(item.getRecordType())) || ItemStatus.isArchived(item.getStatus()) || ItemStatus.CLAIM_PENDING.equals(ItemStatus.canonical(item.getStatus())) || ItemStatus.VERIFIED.equals(ItemStatus.canonical(item.getStatus()))) {
            return null;
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Category match is the strongest single structured signal.
        if (same(report.getCategory(), item.getCategory())) {
            score += 25;
            reasons.add("same category");
        }
        // Color/brand only count when the report actually specified them.
        if (!blank(report.getColor()) && same(report.getColor(), item.getColor())) {
            score += 15;
            reasons.add("same color");
        }
        if (!blank(report.getBrand()) && same(report.getBrand(), item.getBrand())) {
            score += 20;
            reasons.add("same brand");
        }

        // Free-text similarity: tokenize the report's title/description/notes...
        Set<String> reportTokens = tokens(String.join(" ",
                value(report.getTitle()),
                value(report.getDescription()),
                value(report.getExtraNotes())
        ));
        // ...and the item's title/description/features/tags.
        Set<String> itemTokens = tokens(String.join(" ",
                value(item.getTitle()),
                value(item.getDescription()),
                value(item.getDistinguishingFeatures()),
                item.getTags() == null ? "" : String.join(" ", item.getTags())
        ));
        // Shared tokens = overlap; reward each (6 pts) up to a +25 cap.
        Set<String> overlap = reportTokens.stream().filter(itemTokens::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!overlap.isEmpty()) {
            score += Math.min(25, overlap.size() * 6);
            // Show up to the first 4 shared words in the reason text.
            reasons.add("similar words: " + String.join(", ", overlap.stream().limit(4).toList()));
        }
        // Temporal proximity: lost-date vs found-date within 3 days.
        if (nearDates(report.getDateLost(), item.getDateFound())) {
            score += 10;
            reasons.add("dates are close");
        }
        // Spatial proximity: either location string contains the other.
        if (!blank(report.getLocationLost()) && !blank(item.getLocationFound())
                && (containsIgnoreCase(report.getLocationLost(), item.getLocationFound())
                || containsIgnoreCase(item.getLocationFound(), report.getLocationLost()))) {
            score += 10;
            reasons.add("near reported location");
        }

        // Only surface matches at/above the confidence floor (35); cap confidence at 98.
        return score >= 35 ? match(item.getId(), Math.min(98, score), reasons, "ai_suggestion") : null;
    }

    /**
     * Merges existing and newly-computed matches into a de-duplicated, ranked list,
     * one entry per found item.
     *
     * <p>Algorithm: concatenate current + next matches, normalize each into the canonical
     * map shape, and key them by found-item id. When duplicates collide for the same item,
     * keep the higher-confidence entry but UNION the reason lists (deduped). Finally sort by
     * confidence descending and cap the result at the top 8 matches.
     *
     * @param currentMatches existing matches on the report (loosely typed; may contain
     *                       legacy String ids or maps; nullable)
     * @param nextMatches    freshly computed candidate matches (nullable)
     * @return up to 8 canonical match maps, highest confidence first
     */
    private List<Map<String, Object>> mergeMatches(List<Object> currentMatches, List<Map<String, Object>> nextMatches) {
        // Keyed by found-item id to collapse duplicates; LinkedHashMap preserves insertion order.
        Map<String, Map<String, Object>> byFoundItem = new LinkedHashMap<>();
        List<Object> combined = new ArrayList<>();
        if (currentMatches != null) {
            combined.addAll(currentMatches);
        }
        if (nextMatches != null) {
            combined.addAll(nextMatches);
        }

        for (Object rawMatch : combined) {
            // Coerce each raw entry into the canonical map shape (skip if unparseable).
            Map<String, Object> normalized = normalizeMatch(rawMatch);
            if (normalized == null) {
                continue;
            }

            String foundItemId = String.valueOf(normalized.get("found_item_id"));
            Map<String, Object> existing = byFoundItem.get(foundItemId);
            // Replace the stored entry only when this one is at least as confident...
            if (existing == null || number(normalized.get("confidence")) >= number(existing.get("confidence"))) {
                // ...but combine reasons from both so no rationale is lost.
                List<String> reasons = new ArrayList<>();
                reasons.addAll(stringList(existing == null ? null : existing.get("reasons")));
                reasons.addAll(stringList(normalized.get("reasons")));
                normalized.put("reasons", reasons.stream().filter(reason -> !blank(reason)).distinct().toList());
                byFoundItem.put(foundItemId, normalized);
            }
        }

        // Rank by confidence (descending via negation) and keep the top 8.
        return byFoundItem.values().stream()
                .sorted(Comparator.comparingInt(match -> -number(match.get("confidence"))))
                .limit(8)
                .toList();
    }

    /**
     * Coerces a loosely-typed stored/incoming match into the canonical match map.
     *
     * <p>Handles three shapes: a bare String (legacy: just a found-item id → confidence 0,
     * source "legacy_match"); a Map (reads found_item_id/foundItemId, clamps confidence to
     * 0..100, carries reasons/source/created_date with defaults); anything else → null.
     *
     * @param rawMatch the raw match value (String, Map, or other)
     * @return a canonical match map, or null if it has no usable found-item id
     */
    private Map<String, Object> normalizeMatch(Object rawMatch) {
        // Legacy format: the match was stored as just the found-item id string.
        if (rawMatch instanceof String foundItemId) {
            return match(foundItemId, 0, List.of(), "legacy_match");
        }
        // Anything that isn't a map can't be interpreted.
        if (!(rawMatch instanceof Map<?, ?> rawMap)) {
            return null;
        }

        // Accept either snake_case or camelCase id key; bail without one.
        String foundItemId = firstNonBlank(rawMap.get("found_item_id"), rawMap.get("foundItemId"));
        if (blank(foundItemId)) {
            return null;
        }

        return match(
                foundItemId,
                Math.max(0, Math.min(100, number(rawMap.get("confidence")))),       // clamp confidence to 0..100
                stringList(rawMap.get("reasons")),
                value(firstNonBlank(rawMap.get("source"), "ai_suggestion")),         // default source
                value(firstNonBlank(rawMap.get("created_date"), rawMap.get("createdDate"), clock.now())) // default to now
        );
    }

    /**
     * Convenience overload of {@link #match(String, int, List, String, String)} that
     * stamps the created date with the current time.
     *
     * @param foundItemId the matched found item's id
     * @param confidence  the confidence score
     * @param reasons     human-readable rationale strings
     * @param source      the match source (e.g. "ai_suggestion", "finder_response")
     * @return a canonical match map dated now
     */
    private Map<String, Object> match(String foundItemId, int confidence, List<String> reasons, String source) {
        return match(foundItemId, confidence, reasons, source, clock.now());
    }

    /**
     * Builds the canonical match map with all five standard keys, applying defaults
     * (dedup/blank-filter reasons, default source "ai_suggestion", default date now).
     *
     * @param foundItemId the matched found item's id
     * @param confidence  the confidence score
     * @param reasons     rationale strings (null → empty)
     * @param source      the match source (blank → "ai_suggestion")
     * @param createdDate the creation timestamp (blank → now)
     * @return an ordered map with keys: found_item_id, confidence, reasons, source, created_date
     */
    private Map<String, Object> match(String foundItemId, int confidence, List<String> reasons, String source, String createdDate) {
        // LinkedHashMap so the JSON field order is stable/predictable.
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("found_item_id", foundItemId);
        match.put("confidence", confidence);
        match.put("reasons", reasons == null ? List.of() : reasons.stream().filter(reason -> !blank(reason)).distinct().toList());
        match.put("source", blank(source) ? "ai_suggestion" : source);
        match.put("created_date", blank(createdDate) ? clock.now() : createdDate);
        return match;
    }

    /**
     * Extracts the found-item id from a raw match entry (String or Map form).
     *
     * @param rawMatch the raw match value
     * @return the found-item id, or "" if it cannot be determined
     */
    private String matchFoundItemId(Object rawMatch) {
        if (rawMatch instanceof String foundItemId) {
            return foundItemId;
        }
        if (rawMatch instanceof Map<?, ?> rawMap) {
            return firstNonBlank(rawMap.get("found_item_id"), rawMap.get("foundItemId"));
        }
        return "";
    }

    /**
     * Decides whether re-merging the current matches would yield the same result as the
     * proposed next matches — used to skip needless saves.
     *
     * <p>Compares stable signatures (id:confidence:source) of the normalized current set
     * against the next set.
     *
     * @param currentMatches the report's existing matches
     * @param nextMatches    the proposed merged matches
     * @return true if the two sets are equivalent by signature
     */
    private boolean sameMatches(List<Object> currentMatches, List<Map<String, Object>> nextMatches) {
        return Objects.equals(matchSignatures(mergeMatches(currentMatches, List.of())), matchSignatures(nextMatches));
    }

    /**
     * Produces a comparable signature for each match ("foundId:confidence:source") so two
     * match lists can be compared for meaningful equality.
     *
     * @param matches the canonical match maps
     * @return the per-match signature strings, in order
     */
    private List<String> matchSignatures(List<Map<String, Object>> matches) {
        return matches.stream()
                .map(match -> value(match.get("found_item_id")) + ":" + number(match.get("confidence")) + ":" + value(match.get("source")))
                .toList();
    }

    /**
     * Null-safe accessor for a report's matched-items list.
     *
     * @param report the lost report
     * @return its matched items, or an empty list when null
     */
    private List<Object> safeMatches(LostReport report) {
        return report.getMatchedItems() == null ? List.of() : report.getMatchedItems();
    }

    /**
     * Tests whether two ISO date strings are within 3 calendar days of each other.
     *
     * @param left  one date (ISO-8601, e.g. "2026-06-28")
     * @param right the other date
     * @return true if both parse and differ by &le; 3 days; false on any parse error
     */
    private boolean nearDates(String left, String right) {
        try {
            // Absolute day difference between the two parsed dates.
            return Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(left), LocalDate.parse(right))) <= 3;
        } catch (Exception ignored) {
            // Unparseable/missing dates simply don't count as "near".
            return false;
        }
    }

    /**
     * Tokenizes free text into a set of distinct lower-case words longer than 2 chars.
     *
     * @param text the input text
     * @return an ordered set of significant tokens (length &gt; 2)
     */
    private Set<String> tokens(String text) {
        // Normalize, split on non-alphanumerics, drop short/noise tokens, keep insertion order.
        return TOKEN_SPLIT.splitAsStream(normalize(text))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Coerces a loosely-typed value into a list of strings.
     *
     * @param value expected to be a List (other types → empty list)
     * @return the elements stringified, or an empty list
     */
    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Coerces a loosely-typed value into an int (Numbers truncated, strings parsed).
     *
     * @param value a Number, numeric String, or other
     * @return the int value, or 0 when not parseable
     */
    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Case/whitespace-insensitive equality, treating a blank left side as "no match".
     *
     * @param left  the reference value (blank → false)
     * @param right the comparison value
     * @return true if both normalize equal
     */
    private boolean same(String left, String right) {
        return !blank(left) && normalize(left).equals(normalize(right));
    }

    /**
     * Case-insensitive substring test on normalized strings.
     *
     * @param text the haystack
     * @param part the needle
     * @return true if normalized {@code text} contains normalized {@code part}
     */
    private boolean containsIgnoreCase(String text, String part) {
        return normalize(text).contains(normalize(part));
    }

    /**
     * Returns the first non-blank value (stringified) among the arguments.
     *
     * @param values candidate values in priority order
     * @return the first non-blank stringified value, or "" if none qualify
     */
    private String firstNonBlank(Object... values) {
        for (Object next : values) {
            String text = value(next);
            if (!blank(text)) {
                return text;
            }
        }
        return "";
    }

    /**
     * Null-safe stringification.
     *
     * @param value any object (null → "")
     * @return the string form, or "" when null
     */
    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Normalizes a string for comparison: stringify, trim, lower-case (root locale).
     *
     * @param value the input
     * @return the normalized form
     */
    private String normalize(String value) {
        return value(value).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Null/blank string test helper.
     *
     * @param value the string to check
     * @return true if null or whitespace-only
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
