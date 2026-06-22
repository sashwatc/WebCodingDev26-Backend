package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import java.util.List;

public interface AiMatchClient {
    List<AiMatchResult> rankMatches(LostReport report, List<FoundItem> candidates);

    record AiMatchResult(String foundItemId, Integer confidence, List<String> reasons) {
    }
}
