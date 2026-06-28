package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassResponse;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReturnPassService}, covering the QR/code redemption and read-access rules
 * for return passes.
 *
 * <p>All repositories and collaborator services are mocked, and a {@link FixedClock} pins "now" to
 * 2026-06-22 so expiry can be tested deterministically. Tests verify that a valid redemption
 * atomically completes the whole pickup lifecycle (pass redeemed, claim completed, item archived,
 * custody ledger + recovery case updated, notification fired), that redemption cannot repeat, that
 * expired or wrong-code redemptions are rejected without mutating state, and that pass reads are
 * authorized (admins/owners only) with the one-time code only exposed to the claimant.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReturnPassServiceTest {
    // The claimant who owns the pass.
    private static final String CLAIMANT_EMAIL = "riley.chen@pleasantvalley.edu";
    // The admin/staff actor performing redemptions.
    private static final String ADMIN_EMAIL = "avery.patel@pleasantvalley.edu";
    // The correct one-time redemption code for the fixture pass.
    private static final String CODE = "654321";

    // Mocked return-pass repository.
    @Mock
    private ReturnPassRepository returnPasses;
    // Mocked claim repository.
    @Mock
    private ClaimRepository claims;
    // Mocked found-item repository.
    @Mock
    private FoundItemRepository foundItems;
    // Mocked notification repository.
    @Mock
    private NotificationRepository notifications;
    // Mocked custody ledger; verified to record handoff/returned events.
    @Mock
    private CustodyLedgerService custodyLedgerService;
    // Mocked recovery-case service; verified to mark the case returned.
    @Mock
    private RecoveryCaseService recoveryCaseService;
    // Mocked notification dispatcher; verified to emit the item-returned pulse.
    @Mock
    private RecoveryPulseDispatcher recoveryPulse;
    // Mocked post-completion cleanup service.
    @Mock
    private CompletionCleanupService completionCleanup;
    // Mocked authorization service used by the read-access (get) tests.
    @Mock
    private DemoAuthorizationService authorizationService;

    /**
     * Scenario: an admin redeems a valid, unexpired pass with the correct code.
     * Arrange: an active pass (expires 2026-06-24, after fixed now), an approved claim, and a
     * claimed item; all repository saves echo their argument.
     * Act: call redeem with the correct code as the admin.
     * Assert: response status is "redeemed"; the pass records redeemedAt/redeemedBy(admin); the
     * claim becomes "completed" with a received-confirmed timestamp; the item is archived and marked
     * claim-confirmed. Verifies custody ledger gets handoff_verified then returned events, the
     * recovery case is marked returned, and the item-returned pulse fires. Passing proves redemption
     * atomically completes the entire pickup lifecycle.
     */
    @Test
    void redeemCompletesPickupLifecycleAtomically() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z"); // unexpired (after fixed now)
        Claim claim = claim("claim_001", "approved"); // claim approved -> eligible to complete
        FoundItem item = foundItem("found_001", "claimed");

        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(claims.findById("claim_001")).thenReturn(Optional.of(claim));
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnPassResponse response = service().redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin());

        assertThat(response.status()).isEqualTo("redeemed"); // pass now redeemed
        assertThat(pass.getRedeemedAt()).isNotBlank(); // redemption timestamped
        assertThat(pass.getRedeemedBy()).isEqualTo(ADMIN_EMAIL); // recorded the acting admin
        assertThat(claim.getStatus()).isEqualTo("completed"); // claim closed out
        assertThat(claim.getReceivedConfirmedAt()).isNotBlank(); // receipt confirmed
        assertThat(ItemStatus.isArchived(item.getStatus())).isTrue(); // item archived post-return
        assertThat(item.getClaimConfirmed()).isTrue(); // confirmed handed to claimant

        verify(custodyLedgerService).appendEvent(eq("found_001"), eq("handoff_verified"), any(), any(), any(), any(), any()); // custody: verified at handoff
        verify(custodyLedgerService).appendEvent(eq("found_001"), eq("returned"), any(), any(), any(), any(), any()); // custody: returned
        verify(recoveryCaseService).markReturned("claim_001", "found_001"); // case closed
        verify(recoveryPulse).itemReturned(pass); // claimant notified
    }

    /**
     * Scenario: a pass that has already been redeemed cannot be redeemed a second time.
     * Arrange: same valid fixtures as the happy path.
     * Act: redeem once successfully, then redeem again with the same code.
     * Assert: the second call throws {@link ConflictException}. Passing proves redemption is
     * single-use and idempotency is enforced against double pickups.
     */
    @Test
    void redeemCannotHappenTwice() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        Claim claim = claim("claim_001", "approved");
        FoundItem item = foundItem("found_001", "claimed");

        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(claims.findById("claim_001")).thenReturn(Optional.of(claim));
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnPassService service = service();
        service.redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin()); // first redemption succeeds

        assertThatThrownBy(() -> service.redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin()))
                .isInstanceOf(ConflictException.class); // second redemption is a conflict
    }

    /**
     * Scenario: a pass whose expiry (2026-06-20) is before the fixed now (2026-06-22) is presented.
     * Arrange: an active-but-expired pass; only the pass lookup/save are stubbed.
     * Act: redeem with the correct code.
     * Assert: throws {@link ConflictException} mentioning "xpired"; the pass is flipped to status
     * "expired"; and no claim/item is saved and the recovery case is never marked returned. Passing
     * proves expired passes are rejected and leave the pickup state untouched.
     */
    @Test
    void expiredPassCannotCompletePickup() {
        ReturnPass pass = pass("pass_expired", "active", CODE, "2026-06-20T12:00:00Z"); // expiry before fixed now
        when(returnPasses.findById("pass_expired")).thenReturn(Optional.of(pass));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service().redeem("pass_expired", new ReturnPassRedeemRequest(CODE), admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("xpired"); // matches "expired"/"Expired"

        assertThat(pass.getStatus()).isEqualTo("expired"); // pass marked expired
        verify(claims, never()).save(any(Claim.class)); // claim untouched
        verify(foundItems, never()).save(any(FoundItem.class)); // item untouched
        verify(recoveryCaseService, never()).markReturned(any(), any()); // case not closed
    }

    /**
     * Scenario: redemption is attempted with the wrong one-time code.
     * Arrange: an active pass with the correct code; only the pass lookup stubbed.
     * Act: redeem with "000000" (incorrect).
     * Assert: throws {@link BadRequestException}; the pass stays "active" and nothing is saved.
     * Passing proves a bad code is rejected and leaves the pass usable for a correct retry.
     */
    @Test
    void redeemRejectsMismatchedOneTimeCode() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));

        assertThatThrownBy(() -> service().redeem("pass_001", new ReturnPassRedeemRequest("000000"), admin())) // wrong code
                .isInstanceOf(BadRequestException.class);

        assertThat(pass.getStatus()).isEqualTo("active"); // still redeemable
        verify(returnPasses, never()).save(any(ReturnPass.class)); // no state change persisted
    }

    /**
     * Scenario: a non-admin who is not the claimant tries to read a pass.
     * Arrange: an active pass; the reader resolves to "intruder@..." and is not an admin.
     * Act: call get with the intruder email.
     * Assert: throws {@link ForbiddenException}. Passing proves passes (and their one-time codes) are
     * not readable by unauthorized users.
     */
    @Test
    void getDeniesUnauthorizedReaderAndHidesOneTimeCode() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin("intruder@pleasantvalley.edu")).thenReturn(false); // not admin
        when(authorizationService.resolveEmail("intruder@pleasantvalley.edu")).thenReturn("intruder@pleasantvalley.edu"); // not the owner

        assertThatThrownBy(() -> service().get("pass_001", "intruder@pleasantvalley.edu", authorizationService))
                .isInstanceOf(ForbiddenException.class); // access denied
    }

    /**
     * Scenario: an unauthenticated reader requests a pass that happens to have a blank claimant email
     * (guarding against a blank==blank ownership match bypass).
     * Arrange: a pass with claimantEmail=null; the caller resolves to "" and is not admin.
     * Act: call get with a null caller email.
     * Assert: throws {@link ForbiddenException}. Passing proves a blank/anonymous caller cannot read
     * an owner-less pass by matching empty emails.
     */
    @Test
    void getDeniesUnauthenticatedReaderEvenWhenClaimantEmailIsBlank() {
        ReturnPass pass = pass("pass_blank", "active", CODE, "2026-06-24T12:00:00Z");
        pass.setClaimantEmail(null); // pass has no owner email
        when(returnPasses.findById("pass_blank")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin(null)).thenReturn(false);
        when(authorizationService.resolveEmail(null)).thenReturn(""); // anonymous caller resolves blank

        assertThatThrownBy(() -> service().get("pass_blank", null, authorizationService))
                .isInstanceOf(ForbiddenException.class); // blank != blank does not grant access
    }

    /**
     * Scenario: the legitimate claimant reads their own pass.
     * Arrange: an active pass owned by CLAIMANT_EMAIL; the caller resolves to CLAIMANT_EMAIL and is
     * not admin.
     * Act: call get as the claimant.
     * Assert: the response returns the claim id and exposes the one-time code (CODE). Passing proves
     * the owning claimant can retrieve their pass including the code needed for pickup.
     */
    @Test
    void getAllowsClaimantToReadOwnPass() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin(CLAIMANT_EMAIL)).thenReturn(false);
        when(authorizationService.resolveEmail(CLAIMANT_EMAIL)).thenReturn(CLAIMANT_EMAIL); // caller is the owner

        ReturnPassResponse response = service().get("pass_001", CLAIMANT_EMAIL, authorizationService);

        assertThat(response.claimId()).isEqualTo("claim_001");
        assertThat(response.oneTimeCode()).isEqualTo(CODE); // owner sees the code
    }

    // Helper: build the service under test with the fixed clock and all mocked collaborators.
    private ReturnPassService service() {
        return new ReturnPassService(
                returnPasses, claims, foundItems, notifications, custodyLedgerService, recoveryCaseService, new FixedClock(), recoveryPulse, completionCleanup);
    }

    // Helper: build a ReturnPass fixture owned by the claimant for found_001/claim_001.
    private ReturnPass pass(String id, String status, String code, String expiresAt) {
        ReturnPass pass = new ReturnPass();
        pass.setId(id);
        pass.setClaimId("claim_001");
        pass.setFoundItemId("found_001");
        pass.setClaimantEmail(CLAIMANT_EMAIL);
        pass.setPickupLocation("PVHS Main Office pickup station");
        pass.setStatus(status);
        pass.setOneTimeCode(code);
        pass.setToken("internal-token");
        pass.setExpiresAt(expiresAt);
        return pass;
    }

    // Helper: build a Claim fixture for the claimant on found_001 with the given status.
    private Claim claim(String id, String status) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId("found_001");
        claim.setClaimantEmail(CLAIMANT_EMAIL);
        claim.setStatus(status);
        return claim;
    }

    // Helper: build a FoundItem fixture with the given status.
    private FoundItem foundItem(String id, String status) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Blue JanSport Backpack");
        item.setCategory("bags");
        item.setStatus(status);
        return item;
    }

    // Helper: build the admin actor performing redemptions.
    private AppUser admin() {
        AppUser admin = new AppUser();
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole("admin");
        return admin;
    }

    // Test clock pinned to 2026-06-22 so expiry comparisons are deterministic.
    private static class FixedClock extends ClockService {
        @Override
        public String now() {
            return "2026-06-22T12:00:00Z";
        }
    }
}
