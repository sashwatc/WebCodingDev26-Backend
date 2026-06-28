package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.RecoveryCaseRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import com.FBLA.WebCodingDev26Backend.repository.WatchedItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Removes a found item and every record that references it once its claim is both
 * approved and completed. At that point the item's lifecycle is finished, so it is
 * deleted from the dashboard along with its claims, return passes, watchers,
 * recovery cases, custody ledger, and any lost-report match suggestions — leaving
 * no orphaned references behind in the admin dashboard, student views, lost
 * reports, or recovery cases.
 *
 * Each collection is purged independently and best-effort: a failure on one is
 * logged and never aborts the rest.
 */
@Service
public class CompletionCleanupService {
    /** Logger; each per-collection failure is warned and swallowed so cleanup continues. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionCleanupService.class);

    /** Found-item store; the item itself is deleted last. */
    private final FoundItemRepository foundItems;
    /** Claim store; claims for the item (and their return passes) are deleted. */
    private final ClaimRepository claims;
    /** Return-pass store; passes tied to the item's claims or the item directly are deleted. */
    private final ReturnPassRepository returnPasses;
    /** Watched-item / saved-alert store; watchers on the item are deleted. */
    private final WatchedItemRepository watchedItems;
    /** Recovery-case store; cases that selected the item are closed as "returned" (not deleted). */
    private final RecoveryCaseRepository recoveryCases;
    /** Lost-report store; match suggestions referencing the item are stripped and reports resolved. */
    private final LostReportRepository lostReports;
    /** Custody-ledger store; the item's ledger entries are deleted. */
    private final CustodyEventRepository custodyEvents;
    /** Supplies timestamps when reports/cases are updated during cleanup. */
    private final ClockService clock;

    /** Injects all repositories and the clock used during cascade cleanup. */
    public CompletionCleanupService(
            FoundItemRepository foundItems,
            ClaimRepository claims,
            ReturnPassRepository returnPasses,
            WatchedItemRepository watchedItems,
            RecoveryCaseRepository recoveryCases,
            LostReportRepository lostReports,
            CustodyEventRepository custodyEvents,
            ClockService clock
    ) {
        this.foundItems = foundItems;
        this.claims = claims;
        this.returnPasses = returnPasses;
        this.watchedItems = watchedItems;
        this.recoveryCases = recoveryCases;
        this.lostReports = lostReports;
        this.custodyEvents = custodyEvents;
        this.clock = clock;
    }

    /**
     * Cascade-delete the found item and everything that points at it. Safe to call
     * with an unknown id; it simply purges whatever references exist.
     *
     * <p>Order matters and each step is wrapped in {@link #safely} so a failure in one
     * collection is logged and never aborts the others. Recovery cases are an
     * exception to deletion — they are closed as "returned" so the student keeps a
     * completed timeline. The item itself is removed last.
     *
     * @param foundItemId id of the fully-resolved found item to purge; null/blank is a no-op
     *                    Side effects: multiple DB deletes and updates across repositories.
     */
    public void purgeCompletedItem(String foundItemId) {
        // Nothing to do without a real id.
        if (foundItemId == null || foundItemId.isBlank()) {
            return;
        }

        // Claims for this item, plus each claim's return passes.
        safely("claims", () -> {
            for (Claim claim : claims.findByFoundItemId(foundItemId)) {
                safely("return passes for claim " + claim.getId(),
                        () -> returnPasses.findByClaimId(claim.getId()).forEach(returnPasses::delete));
                claims.deleteById(claim.getId());
            }
        });

        // Return passes that referenced the item directly (defensive).
        safely("return passes", () -> returnPasses.findAll().stream()
                .filter(pass -> foundItemId.equals(pass.getFoundItemId()))
                .forEach(returnPasses::delete));

        // Watchers / saved alerts on the item.
        safely("watched items", () -> watchedItems.findAll().stream()
                .filter(watched -> foundItemId.equals(watched.getFoundItemId()))
                .forEach(watchedItems::delete));

        // Recovery cases that selected the item: close them out as "returned"
        // rather than delete. The case belongs to the lost report (which survives),
        // so the student keeps a completed recovery timeline instead of seeing the
        // case vanish and "Refresh plan" resurrect an empty one.
        safely("recovery cases", () -> recoveryCases.findAll().stream()
                .filter(recoveryCase -> foundItemId.equals(recoveryCase.getSelectedFoundItemId()))
                .forEach(recoveryCase -> {
                    recoveryCase.setStatus("returned");
                    recoveryCase.setUpdatedDate(clock.now());
                    recoveryCases.save(recoveryCase);
                }));

        // Custody ledger entries for the item.
        safely("custody events",
                () -> custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId).forEach(custodyEvents::delete));

        // Strip match suggestions referencing the item from every lost report.
        safely("lost report matches", this::stripMatches, foundItemId);

        // Finally the item itself.
        safely("found item", () -> foundItems.deleteById(foundItemId));
    }

    /**
     * Removes any match suggestion that references the purged item from every lost
     * report, and marks each affected report {@code resolved}. Reports with no matching
     * reference are left untouched.
     */
    private void stripMatches(String foundItemId) {
        for (LostReport report : lostReports.findAll()) {
            List<Object> matches = report.getMatchedItems();
            // Skip reports that carry no matches at all.
            if (matches == null || matches.isEmpty()) {
                continue;
            }
            // Keep only the matches that do NOT point at the purged item.
            List<Object> remaining = new ArrayList<>();
            for (Object value : matches) {
                if (!referencesFoundItem(value, foundItemId)) {
                    remaining.add(value);
                }
            }
            // Only rewrite the report if something was actually removed.
            if (remaining.size() != matches.size()) {
                // This report's matched item was just recovered: drop the now-dead
                // match reference and mark the report resolved so it shows a single
                // coherent "Resolved" state instead of a stale active match.
                report.setMatchedItems(remaining);
                report.setStatus("resolved");
                report.setUpdatedDate(clock.now());
                lostReports.save(report);
            }
        }
    }

    /**
     * Tests whether a matched-items entry refers to the given found item. Match entries
     * may be stored in several shapes, so all are handled: a typed {@link MatchSuggestion},
     * a raw map (keyed {@code found_item_id} or {@code foundItemId}), or a bare id string.
     * Any other shape returns false.
     */
    private boolean referencesFoundItem(Object value, String foundItemId) {
        if (value instanceof MatchSuggestion suggestion) {
            return foundItemId.equals(suggestion.getFoundItemId());
        }
        if (value instanceof Map<?, ?> map) {
            // Accept either snake_case or camelCase key for the id.
            Object fid = map.containsKey("found_item_id") ? map.get("found_item_id") : map.get("foundItemId");
            return foundItemId.equals(String.valueOf(fid));
        }
        if (value instanceof String str) {
            return foundItemId.equals(str);
        }
        return false;
    }

    /**
     * Runs a cleanup step, logging and swallowing any {@link RuntimeException} so one
     * failing collection never aborts the rest of the cascade.
     *
     * @param what   label of the step (used in the warning message)
     * @param action the cleanup work to attempt
     */
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Completion cleanup failed for {}: {}", what, exception.getMessage());
        }
    }

    /**
     * Overload of {@link #safely(String, Runnable)} for steps that take a single
     * string argument; logs and swallows any {@link RuntimeException}.
     *
     * @param what   label of the step
     * @param action the cleanup work to attempt
     * @param arg    argument passed to the action (here, the found-item id)
     */
    private void safely(String what, java.util.function.Consumer<String> action, String arg) {
        try {
            action.accept(arg);
        } catch (RuntimeException exception) {
            LOGGER.warn("Completion cleanup failed for {}: {}", what, exception.getMessage());
        }
    }
}
