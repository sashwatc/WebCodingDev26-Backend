package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import java.util.List;

/**
 * Response payload (direction: server -> client) summarizing the outcome of a
 * loss/theft pattern-analysis run. Compares a recent time window against a baseline
 * window over the analyzed reports and returns any prevention alerts that were raised.
 */
public record PatternReviewResult(
        // Overall state of the analysis run (e.g. status code/label describing the result).
        String state,
        // Human-readable summary message describing the analysis outcome.
        String message,
        // Prevention alerts raised by the analysis (e.g. detected loss/theft patterns).
        List<PreventionAlert> alerts,
        // Number of reports examined during this analysis run.
        int analyzedReportCount,
        // Start of the recent comparison window (timestamp string).
        String recentWindowStart,
        // End of the recent comparison window (timestamp string).
        String recentWindowEnd,
        // Start of the baseline comparison window (timestamp string).
        String baselineWindowStart,
        // End of the baseline comparison window (timestamp string).
        String baselineWindowEnd,
        // When this analysis result was computed (timestamp string).
        String calculatedAt
) {
}
