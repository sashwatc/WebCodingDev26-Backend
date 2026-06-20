package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenericEntityServiceTest {
    @Mock
    private LostReportRepository lostReports;
    @Mock
    private ClaimRepository claims;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private AuditLogRepository auditLogs;

    @Test
    void createLostReportAcceptsFrontendPayloadShape() {
        GenericEntityService service = service();

        when(lostReports.save(any(LostReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        assertThat(report.getTitle()).isEqualTo("AirPods Pro case");
        assertThat(report.getItemType()).isEqualTo("AirPods Pro case");
        assertThat(report.getLocationLost()).isEqualTo("Library");
        assertThat(report.getLastSeenLocation()).isEqualTo("Library");
        assertThat(report.getPhotoUrls()).containsExactly("data:image/png;base64,abc");
        assertThat(report.getPhotoUrl()).isEqualTo("data:image/png;base64,abc");
        assertThat(report.getExtraNotes()).isEqualTo("Lost before second period.");
        assertThat(report.getStudentId()).isEqualTo("PV10294");
        assertThat(report.getStatus()).isEqualTo("open");
        assertThat(report.getMatchedItems()).hasSize(1);
        assertThat(((Map<?, ?>) report.getMatchedItems().get(0)).get("found_item_id")).isEqualTo("found_002");
    }

    @Test
    void createClaimAcceptsFrontendPayloadShape() {
        GenericEntityService service = service();

        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

    private GenericEntityService service() {
        return new GenericEntityService(
                lostReports,
                claims,
                notifications,
                auditLogs,
                new PatchMapper(new JacksonConfig().objectMapper()),
                new ClockService()
        );
    }
}
