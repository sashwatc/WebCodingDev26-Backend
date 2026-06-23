package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.model.RecoveryMission;
import java.util.List;

public record RecoveryCaseListItem(
        RecoveryCase recoveryCase,
        LostReport lostReport,
        String nextAction,
        List<String> updates,
        List<RecoveryMission> missions
) {
}
