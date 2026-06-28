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

/**
 * Unit tests for {@link WorkflowService}, which enforces the legal claim status transitions and the
 * item-status side effects that accompany them.
 *
 * <p>Repositories are mocked and a {@link FixedClock} provides deterministic timestamps. Tests
 * verify that a claim cannot jump to "completed" without first being "approved" (validateClaim
 * guard), that an approved -> completed transition is permitted, that an illegal completion never
 * persists item changes, and that applyClaimStatusSideEffects flips the item to VERIFIED on
 * approval and to an archived/returned state on completion.</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {
    // Mocked found-item repository; supplies the item whose status the workflow inspects/updates.
    @Mock
    private FoundItemRepository foundItems;
    // Mocked lost-report repository (collaborator of the workflow service).
    @Mock
    private LostReportRepository lostReports;
    // Mocked claim repository (collaborator of the workflow service).
    @Mock
    private ClaimRepository claims;

    /**
     * Scenario: a claim is moved directly from "under_review" to "completed" without ever being
     * approved.
     * Arrange: the backing item is CLAIM_PENDING; previous claim status under_review, new status
     * completed.
     * Act: call validateClaim(completed, previous).
     * Assert: throws {@link BadRequestException} whose message mentions "approved". Passing proves
     * completion requires a prior approval step.
     */
    @Test
    void claimCannotBeCompletedWithoutPriorApproval() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.CLAIM_PENDING)));

        Claim previous = claim("claim_001", "found_001", "under_review"); // never approved
        Claim completed = claim("claim_001", "found_001", "completed"); // illegal jump

        assertThatThrownBy(() -> workflow.validateClaim(completed, previous))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approved"); // error explains the missing approval
    }

    /**
     * Scenario: a properly approved claim is completed.
     * Arrange: the backing item is VERIFIED; previous status approved, new status completed.
     * Act: call validateClaim(completed, previous).
     * Assert: no exception is thrown. Passing proves the approved -> completed transition is the
     * legal happy path.
     */
    @Test
    void approvedClaimCanTransitionToCompleted() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.VERIFIED)));

        Claim previous = claim("claim_001", "found_001", "approved"); // approved first
        Claim completed = claim("claim_001", "found_001", "completed");

        assertThatCode(() -> workflow.validateClaim(completed, previous)).doesNotThrowAnyException(); // allowed
    }

    /**
     * Scenario: an illegal completion (from "submitted", skipping approval) is validated.
     * Arrange: the backing item is CLAIM_PENDING; previous status submitted, new status completed.
     * Act: call validateClaim(completed, previous).
     * Assert: throws {@link BadRequestException}, and crucially the found item is never saved.
     * Passing proves a rejected transition has no side effects on item state.
     */
    @Test
    void itemIsNotMarkedReturnedWhenCompletionIsRejectedBeforeApproval() {
        WorkflowService workflow = workflow();
        when(foundItems.findById("found_001")).thenReturn(Optional.of(foundItem("found_001", ItemStatus.CLAIM_PENDING)));

        Claim previous = claim("claim_001", "found_001", "submitted"); // not approved
        Claim completed = claim("claim_001", "found_001", "completed");

        assertThatThrownBy(() -> workflow.validateClaim(completed, previous))
                .isInstanceOf(BadRequestException.class);
        verify(foundItems, never()).save(any(FoundItem.class)); // no item mutation on rejected transition
    }

    /**
     * Scenario: the item status side effects are applied across the full approve-then-complete flow.
     * Arrange: a CLAIM_PENDING item; save echoes the entity.
     * Act: apply side effects for submitted -> approved, then for approved -> completed (with a
     * received-confirmed timestamp).
     * Assert: after approval the item's canonical status is VERIFIED; after completion the item is
     * archived and marked claim-confirmed. Passing proves approval verifies the item and completion
     * returns/archives it.
     */
    @Test
    void approvedClaimMarksItemVerifiedAndCompletionMarksItReturned() {
        WorkflowService workflow = workflow();
        FoundItem item = foundItem("found_001", ItemStatus.CLAIM_PENDING);
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Step 1: approval transition should mark the item VERIFIED.
        Claim submitted = claim("claim_001", "found_001", "submitted");
        Claim approved = claim("claim_001", "found_001", "approved");
        workflow.applyClaimStatusSideEffects(approved, submitted);
        assertThat(ItemStatus.canonical(item.getStatus())).isEqualTo(ItemStatus.VERIFIED); // item verified on approval

        // Step 2: completion transition should archive the item and confirm the claim.
        Claim completed = claim("claim_001", "found_001", "completed");
        completed.setReceivedConfirmedAt("2026-06-22T12:00:00Z"); // receipt confirmed timestamp
        workflow.applyClaimStatusSideEffects(completed, approved);
        assertThat(ItemStatus.isArchived(item.getStatus())).isTrue(); // item archived after return
        assertThat(item.getClaimConfirmed()).isTrue(); // confirmed returned to claimant
    }

    // Helper: build the workflow service with mocked repositories and the fixed clock.
    private WorkflowService workflow() {
        return new WorkflowService(foundItems, lostReports, claims, new FixedClock());
    }

    // Helper: build a FoundItem fixture with the given canonical status.
    private FoundItem foundItem(String id, String status) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Blue JanSport Backpack");
        item.setCategory("bags");
        item.setStatus(status);
        return item;
    }

    // Helper: build a Claim fixture for the given item with the given status.
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

    // Test clock pinned to 2026-06-22 for deterministic timestamps.
    private static class FixedClock extends ClockService {
        @Override
        public String now() {
            return "2026-06-22T12:00:00Z";
        }
    }
}
