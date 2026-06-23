package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.PreventionAlert;
import java.util.List;

public record PatternReviewResult(
        String state,
        String message,
        List<PreventionAlert> alerts,
        int analyzedReportCount,
        String recentWindowStart,
        String recentWindowEnd,
        String baselineWindowStart,
        String baselineWindowEnd,
        String calculatedAt
) {
}
