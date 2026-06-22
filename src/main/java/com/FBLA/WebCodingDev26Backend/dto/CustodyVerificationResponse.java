package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;

public record CustodyVerificationResponse(
        String foundItemId,
        boolean verified,
        Integer eventCount,
        List<String> issues
) {
}
