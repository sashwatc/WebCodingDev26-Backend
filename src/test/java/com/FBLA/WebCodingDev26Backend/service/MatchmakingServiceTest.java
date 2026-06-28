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

/**
 * Unit tests for {@link MatchmakingService}, which ranks found items against a lost report.
 *
 * <p>Repositories and the {@link AiMatchClient} are mocked. The tests verify the deterministic
 * local scoring engine (category/brand/color/location signals), how AI results are merged on top of
 * the deterministic score without lowering it, filtering of unapproved or visibility-restricted
 * found items, and tolerant reading of legacy/heterogeneous match shapes already stored on a
 * report. The service is constructed with fixed scoring thresholds (5, 35) via {@link #service()}.</p>
 */
@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {
    // Mocked lost-report repository (source of the report and sink for saved matches).
    @Mock
    private LostReportRepository lostReports;
    // Mocked found-item repository supplying the candidate pool.
    @Mock
    private FoundItemRepository foundItems;
    // Mocked AI ranking client; stubbed to be unavailable or to augment reasons per test.
    @Mock
    private AiMatchClient aiMatchClient;

    /**
     * Scenario: refresh matches when the AI client returns nothing (unavailable/degraded).
     * Arrange: a lost blue JanSport backpack report; the candidate pool holds a matching backpack
     * and an unrelated bottle; AI returns an empty list; save echoes the report.
     * Act: call refreshMatchesForLostReport.
     * Assert: exactly one match is produced — the backpack (found_002) — with source
     * "deterministic", a high confidence (>=90), and reasons covering category/brand/color. The
     * captor confirms the single match was persisted onto the report. Passing proves local scoring
     * stands alone when AI is unavailable.
     */
    @Test
    void refreshMatchesForLostReportUsesLocalScoringWhenAiIsUnavailable() {
        LostReport report = lostBackpackReport();
        FoundItem backpack = foundBackpack();
        FoundItem bottle = foundBottle();

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(bottle, backpack)); // candidate pool: 1 relevant, 1 noise
        when(aiMatchClient.rankMatches(eq(report), any())).thenReturn(List.of()); // AI returns nothing
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).hasSize(1); // only the backpack qualifies, bottle filtered by low score
        assertThat(matches.get(0).getFoundItemId()).isEqualTo("found_002"); // the matching backpack
        assertThat(matches.get(0).getSource()).isEqualTo("deterministic"); // produced by local scoring
        assertThat(matches.get(0).getConfidence()).isGreaterThanOrEqualTo(90); // strong signal overlap
        assertThat(matches.get(0).getReasons()).contains("category match", "brand match", "color match"); // explainable reasons

        // Confirm the computed match was written back onto the report.
        ArgumentCaptor<LostReport> reportCaptor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getMatchedItems()).hasSize(1);
    }

    /**
     * Scenario: AI is available and adds an explanatory reason for a candidate the local engine
     * already scores highly.
     * Arrange: same report; pool has the matching backpack; AI returns a result for found_002 with
     * a lower confidence (82) plus an extra reason.
     * Act: call refreshMatchesForLostReport.
     * Assert: the single match's source becomes "ai_assisted", confidence stays >=90 (the higher
     * deterministic score is kept rather than dropping to AI's 82), and the AI reason is merged in.
     * Passing proves AI enriches reasons without weakening a strong deterministic score.
     */
    @Test
    void refreshMatchesForLostReportKeepsDeterministicScoreWhenAiAddsReasons() {
        LostReport report = lostBackpackReport();
        FoundItem backpack = foundBackpack();

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(backpack));
        when(aiMatchClient.rankMatches(eq(report), any())).thenReturn(List.of(
                new AiMatchClient.AiMatchResult("found_002", 82, List.of("AI matched the backpack details.")) // AI score lower than deterministic
        ));
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getSource()).isEqualTo("ai_assisted"); // marked as AI-augmented
        assertThat(matches.get(0).getConfidence()).isGreaterThanOrEqualTo(90); // kept higher deterministic score, not 82
        assertThat(matches.get(0).getReasons()).contains("AI matched the backpack details."); // AI reason merged in
    }

    /**
     * Scenario: candidate found items are ineligible for matching (not approved, or restricted
     * visibility).
     * Arrange: a pending_review backpack and a restricted-visibility backpack make up the entire
     * pool.
     * Act: call refreshMatchesForLostReport.
     * Assert: no matches are returned, the report stays "open", and the AI client is never even
     * consulted (nothing eligible to rank). Passing proves ineligible items are filtered before
     * scoring/AI.
     */
    @Test
    void refreshMatchesForLostReportIgnoresUnapprovedOrRestrictedFoundItems() {
        LostReport report = lostBackpackReport();
        FoundItem pendingBackpack = foundBackpack();
        pendingBackpack.setId("found_pending");
        pendingBackpack.setStatus("pending_review"); // not yet approved -> ineligible
        FoundItem restrictedBackpack = foundBackpack();
        restrictedBackpack.setId("found_restricted");
        restrictedBackpack.setRestrictedVisibility(true); // hidden from public matching -> ineligible

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(report));
        when(foundItems.findAll()).thenReturn(List.of(pendingBackpack, restrictedBackpack)); // only ineligible candidates
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MatchSuggestion> matches = service().refreshMatchesForLostReport("lost_001");

        assertThat(matches).isEmpty(); // nothing eligible matched
        assertThat(report.getStatus()).isEqualTo("open"); // status unchanged
        verify(aiMatchClient, never()).rankMatches(any(), any()); // AI not invoked when no candidates
    }

    /**
     * Scenario: a report already stores matches in mixed legacy shapes (a bare id string and a map).
     * Arrange: set matchedItems to ["found_legacy", {found_item_id: found_map, confidence: 77, ...}].
     * Act: call getMatchesForLostReport (read path, no recompute).
     * Assert: both entries are read out in order with ids found_legacy and found_map; the bare
     * string is tagged source "legacy"; the map entry preserves confidence 77. Passing proves the
     * read path tolerantly normalizes heterogeneous historical match data.
     */
    @Test
    void getMatchesForLostReportReadsLegacyMatchShapes() {
        LostReport report = lostBackpackReport();
        // Heterogeneous stored matches: a plain id string and a structured map.
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

        assertThat(matches).extracting((MatchSuggestion match) -> match.getFoundItemId()).containsExactly("found_legacy", "found_map"); // both normalized, order kept
        assertThat(matches.get(0).getSource()).isEqualTo("legacy"); // bare-string entry tagged legacy
        assertThat(matches.get(1).getConfidence()).isEqualTo(77); // map entry's confidence preserved
    }

    // Helper: build the matchmaking service with fixed scoring thresholds (5 and 35).
    private MatchmakingService service() {
        return new MatchmakingService(lostReports, foundItems, aiMatchClient, new ClockService(), 5, 35);
    }

    // Helper: a lost report fixture for a blue JanSport backpack used across the scoring tests.
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

    // Helper: a found-item fixture matching the lost backpack (same category/brand/color/location).
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

    // Helper: an unrelated found-item fixture (black water bottle) used as low-scoring noise.
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
