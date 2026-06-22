package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record PartnerRelayResponse(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String recoveryCaseId,
        String status,
        String publicSummary,
        List<String> redactedMatchReasons,
        String createdDate,
        String updatedDate
) {
}
