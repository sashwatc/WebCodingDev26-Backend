package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.MatchSuggestion;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {
    @Mock
    private LostReportRepository lostReports;
    @Mock
    private FoundItemRepository foundItems;
    @Mock
    private AiMatchClient aiMatchClient;

    @Test
    void refreshMatchesForLostReportUsesLocalScoringWhenAiIsUnavailable() {
        LostReport report = lostBackpackReport();
        FoundItem backpack = foundBackpack();
        FoundItem bottle = foundBottle();

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(bottle, backpack));
        when(aiMatchClient.rankMatches(eq(report), any())).thenReturn(List.of());
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getFoundItemId()).isEqualTo("found_002");
        assertThat(matches.get(0).getSource()).isEqualTo("deterministic");
        assertThat(matches.get(0).getConfidence()).isGreaterThanOrEqualTo(90);
        assertThat(matches.get(0).getReasons()).contains("category match", "brand match", "color match");

        ArgumentCaptor<LostReport> reportCaptor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getMatchedItems()).hasSize(1);
    }

    @Test
    void refreshMatchesForLostReportKeepsDeterministicScoreWhenAiAddsReasons() {
        LostReport report = lostBackpackReport();
        FoundItem backpack = foundBackpack();

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(backpack));
        when(aiMatchClient.rankMatches(eq(report), any())).thenReturn(List.of(
                new AiMatchClient.AiMatchResult("found_002", 82, List.of("AI matched the backpack details."))
        ));
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getSource()).isEqualTo("ai_assisted");
        assertThat(matches.get(0).getConfidence()).isGreaterThanOrEqualTo(90);
        assertThat(matches.get(0).getReasons()).contains("AI matched the backpack details.");
    }

    @Test
    void refreshMatchesForLostReportIgnoresUnapprovedOrRestrictedFoundItems() {
        LostReport report = lostBackpackReport();
        FoundItem pendingBackpack = foundBackpack();
        pendingBackpack.setId("found_pending");
        pendingBackpack.setStatus("pending_review");
        FoundItem restrictedBackpack = foundBackpack();
        restrictedBackpack.setId("found_restricted");
        restrictedBackpack.setRestrictedVisibility(true);

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(pendingBackpack, restrictedBackpack));
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).isEmpty();
        assertThat(report.getStatus()).isEqualTo("open");
        verify(aiMatchClient, never()).rankMatches(any(), any());
    }

    @Test
    void getMatchesForLostReportReadsLegacyMatchShapes() {
        LostReport report = lostBackpackReport();
        report.setMatchedItems(new ArrayList<>(List.of(
                "found_legacy",
                Map.of(
                        "found_item_id", "found_map",
                        "confidence", 77,
                        "reasons", List.of("brand match")
                )
        )));

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));

        List<MatchSuggestion> matches = service().getMatchesForLostReport("lost_001");

        assertThat(matches).extracting((MatchSuggestion match) -> match.getFoundItemId()).containsExactly("found_legacy", "found_map");
        assertThat(matches.get(0).getSource()).isEqualTo("legacy");
        assertThat(matches.get(1).getConfidence()).isEqualTo(77);
    }

    private MatchmakingService service() {
        return new MatchmakingService(lostReports, foundItems, aiMatchClient, new ClockService(), 5, 35);
    }

    private LostReport lostBackpackReport() {
        LostReport report = new LostReport();
        report.setId("lost_001");
        report.setTitle("Missing blue JanSport backpack");
        report.setCategory("bags_cases");
        report.setDescription("Blue JanSport backpack with a tennis keychain.");
        report.setColor("Blue");
        report.setBrand("JanSport");
        report.setLocationLost("Student Lounge");
        report.setDateLost("2026-03-09");
        report.setStatus("open");
        return report;
    }

    private FoundItem foundBackpack() {
        FoundItem item = new FoundItem();
        item.setId("found_002");
        item.setTitle("Blue JanSport Backpack");
        item.setCategory("bags_cases");
        item.setDescription("Royal blue JanSport backpack with math notebook and tennis keychain.");
        item.setColor("Blue");
        item.setBrand("JanSport");
        item.setLocationFound("Student Lounge");
        item.setDateFound("2026-03-09");
        item.setStatus("approved");
        item.setTags(List.of("backpack", "jansport", "blue", "student lounge"));
        return item;
    }

    private FoundItem foundBottle() {
        FoundItem item = new FoundItem();
        item.setId("found_001");
        item.setTitle("Black Hydro Flask Water Bottle");
        item.setCategory("food_containers");
        item.setDescription("Matte black bottle.");
        item.setColor("Black");
        item.setBrand("Hydro Flask");
        item.setLocationFound("Gymnasium");
        item.setDateFound("2026-03-11");
        item.setStatus("approved");
        item.setTags(List.of("water bottle", "black", "gym"));
        return item;
    }
}
