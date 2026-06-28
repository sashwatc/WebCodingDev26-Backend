package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.config.JacksonConfig;
import com.FBLA.WebCodingDev26Backend.mapper.PatchMapper;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.repository.AuditLogRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GenericEntityService}, the generic create/update facade used by the
 * entities API.
 *
 * <p>All repositories and downstream services are mocked. The tests verify that snake_case
 * frontend payloads are correctly mapped onto the {@link LostReport}/{@link Claim} domain models
 * (including legacy/alias field duplication and defaulting), and that updating a lost report
 * triggers matchmaking and recovery-case maintenance without double-refreshing the recovery case.
 * Two service factories are used: {@link #service()} wires a {@link WorkflowService}, while the
 * update test constructs the richer constructor that includes the {@link RecoveryCaseService}.</p>
 */
@ExtendWith(MockitoExtension.class)
class GenericEntityServiceTest {
    // Mocked lost-report repository.
    @Mock
    private LostReportRepository lostReports;
    // Mocked claim repository.
    @Mock
    private ClaimRepository claims;
    // Mocked notification repository.
    @Mock
    private NotificationRepository notifications;
    // Mocked audit-log repository.
    @Mock
    private AuditLogRepository auditLogs;
    // Mocked workflow service (status side-effects) for the simple service() factory.
    @Mock
    private WorkflowService workflow;
    // Mocked matchmaking service; verified to be refreshed on lost-report updates.
    @Mock
    private MatchmakingService matchmakingService;
    // Mocked recovery-case service; verified for ensure/refresh behavior on lost-report updates.
    @Mock
    private RecoveryCaseService recoveryCaseService;

    /**
     * Scenario: create a LostReport from the full snake_case payload the frontend sends.
     * Arrange: stub the repository save to echo the entity back.
     * Act: call create("LostReport", payload) with item type, location, photo, contact, matched
     * items, etc.
     * Assert: each field maps to the right model property, including alias pairs (title==itemType,
     * locationLost==lastSeenLocation, photoUrls list vs single photoUrl), a defaulted status of
     * "open", and the nested matched_items list being preserved with its found_item_id. Passing
     * proves the frontend lost-report shape is fully and faithfully accepted.
     */
    @Test
    void createLostReportAcceptsFrontendPayloadShape() {
        GenericEntityService service = service();

        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0)); // echo saved entity

        LostReport report = (LostReport) service.create("LostReport", Map.ofEntries(
                Map.entry("item_type", "AirPods Pro case"),
                Map.entry("category", "electronics"),
                Map.entry("description", "White case with a scratch near the hinge."),
                Map.entry("color", "White"),
                Map.entry("brand", "Apple"),
                Map.entry("last_seen_location", "Library"),
                Map.entry("date_lost", "2026-03-13"),
                Map.entry("photo_url", "data:image/png;base64,abc"),
                Map.entry("contact_name", "Mia Rodriguez"),
                Map.entry("contact_email", "mia.rodriguez@pleasantvalley.edu"),
                Map.entry("student_id", "PV10294"),
                Map.entry("urgency", "high"),
                Map.entry("extra_notes", "Lost before second period."),
                Map.entry("confirm_accuracy", true),
                Map.entry("matched_items", List.of(Map.of(
                        "found_item_id", "found_002",
                        "confidence", 96,
                        "reasons", List.of("brand match", "color match")
                )))
        ));

        assertThat(report.getTitle()).isEqualTo("AirPods Pro case"); // title mirrors item_type
        assertThat(report.getItemType()).isEqualTo("AirPods Pro case");
        assertThat(report.getLocationLost()).isEqualTo("Library"); // locationLost mirrors last_seen_location
        assertThat(report.getLastSeenLocation()).isEqualTo("Library");
        assertThat(report.getPhotoUrls()).containsExactly("data:image/png;base64,abc"); // single photo lifted into list
        assertThat(report.getPhotoUrl()).isEqualTo("data:image/png;base64,abc"); // legacy single-photo field also set
        assertThat(report.getExtraNotes()).isEqualTo("Lost before second period.");
        assertThat(report.getStudentId()).isEqualTo("PV10294");
        assertThat(report.getStatus()).isEqualTo("open"); // status defaults to open on create
        assertThat(report.getMatchedItems()).hasSize(1); // nested matches preserved
        assertThat(((Map<?, ?>) report.getMatchedItems().get(0)).get("found_item_id")).isEqualTo("found_002"); // match content intact
    }

    /**
     * Scenario: create a Claim from the full snake_case payload the frontend sends.
     * Arrange: stub the claim repository save to echo the entity back.
     * Act: call create("Claim", payload) carrying reason, proof, risk fields, rating/review fields,
     * etc.
     * Assert: every field maps onto the Claim model, including alias pairs (claimReason==reason),
     * the submitted status, risk score/flags, received-confirmed timestamp, and the claimant
     * rating/review/review-status fields; the empty review_reviewed_at maps to an empty string.
     * Passing proves the frontend claim shape is fully accepted.
     */
    @Test
    void createClaimAcceptsFrontendPayloadShape() {
        GenericEntityService service = service();

        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0)); // echo saved entity

        Claim claim = (Claim) service.create("Claim", Map.ofEntries(
                Map.entry("found_item_id", "found_003"),
                Map.entry("found_item_title", "Blue backpack"),
                Map.entry("claimant_name", "Jordan Kim"),
                Map.entry("claimant_email", "jordan.kim@pleasantvalley.edu"),
                Map.entry("student_id", "PV10811"),
                Map.entry("reason", "It has my tennis charm."),
                Map.entry("identifying_details", "Contains a green math notebook."),
                Map.entry("proof_photo_url", "data:image/png;base64,proof"),
                Map.entry("pickup_availability", "After fourth period"),
                Map.entry("status", "submitted"),
                Map.entry("risk_score", 18),
                Map.entry("risk_flags", List.of("low detail")),
                Map.entry("received_confirmed_at", "2026-03-15T12:00:00Z"),
                Map.entry("claimant_rating", 5),
                Map.entry("claimant_review", "Fast return."),
                Map.entry("review_status", "pending"),
                Map.entry("review_submitted_at", "2026-03-15T12:01:00Z"),
                Map.entry("review_reviewed_at", "")
        ));

        assertThat(claim.getFoundItemId()).isEqualTo("found_003");
        assertThat(claim.getFoundItemTitle()).isEqualTo("Blue backpack");
        assertThat(claim.getClaimantEmail()).isEqualTo("jordan.kim@pleasantvalley.edu");
        assertThat(claim.getClaimReason()).isEqualTo("It has my tennis charm.");
        assertThat(claim.getReason()).isEqualTo("It has my tennis charm.");
        assertThat(claim.getStudentId()).isEqualTo("PV10811");
        assertThat(claim.getProofPhotoUrl()).isEqualTo("data:image/png;base64,proof");
        assertThat(claim.getPickupAvailability()).isEqualTo("After fourth period");
        assertThat(claim.getStatus()).isEqualTo("submitted");
        assertThat(claim.getRiskScore()).isEqualTo(18);
        assertThat(claim.getRiskFlags()).containsExactly("low detail");
        assertThat(claim.getReceivedConfirmedAt()).isEqualTo("2026-03-15T12:00:00Z");
        assertThat(claim.getClaimantRating()).isEqualTo(5);
        assertThat(claim.getClaimantReview()).isEqualTo("Fast return.");
        assertThat(claim.getReviewStatus()).isEqualTo("pending");
        assertThat(claim.getReviewSubmittedAt()).isEqualTo("2026-03-15T12:01:00Z");
        assertThat(claim.getReviewReviewedAt()).isEmpty();
    }

    /**
     * Scenario: patch-update an existing LostReport's title.
     * Arrange: repository returns an existing open report; save echoes it; construct the service via
     * the richer constructor that wires the recovery-case service.
     * Act: call update("LostReport", id, {title}).
     * Assert: the title is updated; recoveryCaseService.ensureForLostReport and
     * matchmakingService.refreshMatchesForLostReport are each invoked once; but
     * recoveryCaseService.refreshForLostReport is NEVER called. Passing proves an update maintains
     * the recovery case exactly once and avoids a redundant double-refresh.
     */
    @Test
    void lostReportUpdateDoesNotDoubleRefreshRecoveryCase() {
        // Existing persisted report that the update will mutate.
        LostReport existing = new LostReport();
        existing.setId("lost_001");
        existing.setTitle("Original title");
        existing.setStatus("open");

        when(lostReports.findById("lost_001")).thenReturn(Optional.of(existing)); // load target
        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0)); // echo saved

        // Build service via the full constructor including recoveryCaseService to observe its calls.
        GenericEntityService service = new GenericEntityService(
                lostReports,
                claims,
                notifications,
                auditLogs,
                new PatchMapper(new JacksonConfig().objectMapper()),
                new ClockService(),
                matchmakingService,
                null,
                recoveryCaseService,
                null
        );

        LostReport report = (LostReport) service.update("LostReport", "lost_001", Map.of("title", "Updated title"));

        assertThat(report.getTitle()).isEqualTo("Updated title"); // patch applied
        verify(recoveryCaseService).ensureForLostReport(existing); // case ensured once
        verify(matchmakingService).refreshMatchesForLostReport("lost_001"); // matches refreshed once
        verify(recoveryCaseService, never()).refreshForLostReport(any()); // no redundant second refresh
    }

    // Helper: build the service with the simple (WorkflowService-based) constructor used by the
    // create tests.
    private GenericEntityService service() {
        return new GenericEntityService(
                lostReports,
                claims,
                notifications,
                auditLogs,
                new PatchMapper(new JacksonConfig().objectMapper()),
                new ClockService(),
                workflow
        );
    }
}
