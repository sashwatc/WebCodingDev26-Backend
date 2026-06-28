package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import java.util.List;

/**
 * Strategy interface for ranking how well candidate {@link FoundItem}s match a given
 * {@link LostReport}. Implementations may use heuristics or an external AI model; the
 * matching service depends on this abstraction so the backend can swap match providers.
 */
public interface AiMatchClient {
    /**
     * Scores and ranks candidate found items against a lost report.
     *
     * @param report     the lost report describing what was lost
     * @param candidates the found items to evaluate
     * @return a list of per-candidate match results (typically highest confidence first);
     *         implementations decide ordering and which candidates to include
     */
    List<AiMatchResult> rankMatches(LostReport report, List<FoundItem> candidates);

    /**
     * One ranked match result.
     *
     * @param foundItemId id of the candidate found item
     * @param confidence  match confidence score (implementation-defined scale)
     * @param reasons     human-readable justifications for the score
     */
    record AiMatchResult(String foundItemId, Integer confidence, List<String> reasons) {
    }
}
