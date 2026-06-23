package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record RecoveryCenterResponse(
        RecoveryCenterSummary summary,
        List<RecoveryCaseListItem> cases
) {
}
