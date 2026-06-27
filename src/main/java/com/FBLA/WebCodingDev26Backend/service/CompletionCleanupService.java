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
    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionCleanupService.class);

    private final FoundItemRepository foundItems;
    private final ClaimRepository claims;
    private final ReturnPassRepository returnPasses;
    private final WatchedItemRepository watchedItems;
    private final RecoveryCaseRepository recoveryCases;
    private final LostReportRepository lostReports;
    private final CustodyEventRepository custodyEvents;
    private final ClockService clock;

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
     */
    public void purgeCompletedItem(String foundItemId) {
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

    private void stripMatches(String foundItemId) {
        for (LostReport report : lostReports.findAll()) {
            List<Object> matches = report.getMatchedItems();
            if (matches == null || matches.isEmpty()) {
                continue;
            }
            List<Object> remaining = new ArrayList<>();
            for (Object value : matches) {
                if (!referencesFoundItem(value, foundItemId)) {
                    remaining.add(value);
                }
            }
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

    private boolean referencesFoundItem(Object value, String foundItemId) {
        if (value instanceof MatchSuggestion suggestion) {
            return foundItemId.equals(suggestion.getFoundItemId());
        }
        if (value instanceof Map<?, ?> map) {
            Object fid = map.containsKey("found_item_id") ? map.get("found_item_id") : map.get("foundItemId");
            return foundItemId.equals(String.valueOf(fid));
        }
        if (value instanceof String str) {
            return foundItemId.equals(str);
        }
        return false;
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Completion cleanup failed for {}: {}", what, exception.getMessage());
        }
    }

    private void safely(String what, java.util.function.Consumer<String> action, String arg) {
        try {
            action.accept(arg);
        } catch (RuntimeException exception) {
            LOGGER.warn("Completion cleanup failed for {}: {}", what, exception.getMessage());
        }
    }
}
