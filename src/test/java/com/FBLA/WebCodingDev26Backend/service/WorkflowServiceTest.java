package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {
    @Mock
    private FoundItemRepository foundItems;
    @Mock
    private LostReportRepository lostReports;
    @Mock
    private ClaimRepository claims;

    @Test
    void claimCannotBeCompletedWithoutPriorApproval() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.CLAIM_PENDING)));

        Claim previous = claim("claim_001", "found_001", "under_review");
        Claim completed = claim("claim_001", "found_001", "completed");

        assertThatThrownBy(() -> workflow.validateClaim(completed, previous))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void approvedClaimCanTransitionToCompleted() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.VERIFIED)));

        Claim previous = claim("claim_001", "found_001", "approved");
        Claim completed = claim("claim_001", "found_001", "completed");

        assertThatCode(() -> workflow.validateClaim(completed, previous)).doesNotThrowAnyException();
    }

    @Test
    void itemIsNotMarkedReturnedWhenCompletionIsRejectedBeforeApproval() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.CLAIM_PENDING)));

        Claim previous = claim("claim_001", "found_001", "submitted");
        Claim completed = claim("claim_001", "found_001", "completed");

        assertThatThrownBy(() -> workflow.validateClaim(completed, previous))
                .isInstanceOf(BadRequestException.class);
        verify(foundItems, never()).save(any(FoundItem.class));
    }

    @Test
    void approvedClaimMarksItemVerifiedAndCompletionMarksItReturned() {
        WorkflowService workflow = workflow();
        FoundItem item = foundItem("found_001", ItemStatus.CLAIM_PENDING);
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim submitted = claim("claim_001", "found_001", "submitted");
        Claim approved = claim("claim_001", "found_001", "approved");
        workflow.applyClaimStatusSideEffects(approved, submitted);
        assertThat(ItemStatus.canonical(item.getStatus())).isEqualTo(ItemStatus.VERIFIED);

        Claim completed = claim("claim_001", "found_001", "completed");
        completed.setReceivedConfirmedAt("2026-06-22T12:00:00Z");
        workflow.applyClaimStatusSideEffects(completed, approved);
        assertThat(ItemStatus.isArchived(item.getStatus())).isTrue();
        assertThat(item.getClaimConfirmed()).isTrue();
    }

    private WorkflowService workflow() {
        return new WorkflowService(foundItems, lostReports, claims, new FixedClock());
    }

    private FoundItem foundItem(String id, String status) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Blue JanSport Backpack");
        item.setCategory("bags");
        item.setStatus(status);
        return item;
    }

    private Claim claim(String id, String foundItemId, String status) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId(foundItemId);
        claim.setClaimantName("Riley Chen");
        claim.setClaimantEmail("riley.chen@pleasantvalley.edu");
        claim.setClaimReason("It has my initials inside.");
        claim.setStatus(status);
        return claim;
    }

    private static class FixedClock extends ClockService {
        @Override
        public String now() {
            return "2026-06-22T12:00:00Z";
        }
    }
}
