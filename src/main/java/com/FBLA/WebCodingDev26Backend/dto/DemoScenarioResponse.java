package com.FBLA.WebCodingDev26Backend.dto;

import java.util.List;
import java.util.Map;

public record DemoScenarioResponse(
        String scenario,
        List<String> lostReportIds,
        List<String> recoveryCaseIds,
        List<String> recoveryMissionIds,
        List<String> foundItemIds,
        List<String> claimIds,
        Map<String, Object> details
) {
}
