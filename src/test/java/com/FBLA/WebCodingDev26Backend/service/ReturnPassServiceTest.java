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

@ExtendWith(MockitoExtension.class)
class ReturnPassServiceTest {
    private static final String CLAIMANT_EMAIL = "riley.chen@pleasantvalley.edu";
    private static final String ADMIN_EMAIL = "avery.patel@pleasantvalley.edu";
    private static final String CODE = "654321";

    @Mock
    private ReturnPassRepository returnPasses;
    @Mock
    private ClaimRepository claims;
    @Mock
    private FoundItemRepository foundItems;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private CustodyLedgerService custodyLedgerService;
    @Mock
    private RecoveryCaseService recoveryCaseService;
    @Mock
    private RecoveryPulseDispatcher recoveryPulse;
    @Mock
    private CompletionCleanupService completionCleanup;
    @Mock
    private DemoAuthorizationService authorizationService;

    @Test
    void redeemCompletesPickupLifecycleAtomically() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        Claim claim = claim("claim_001", "approved");
        FoundItem item = foundItem("found_001", "claimed");

        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(claims.findById("claim_001")).thenReturn(Optional.of(claim));
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItems.save(any(FoundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnPassResponse response = service().redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin());

        assertThat(response.status()).isEqualTo("redeemed");
        assertThat(pass.getRedeemedAt()).isNotBlank();
        assertThat(pass.getRedeemedBy()).isEqualTo(ADMIN_EMAIL);
        assertThat(claim.getStatus()).isEqualTo("completed");
        assertThat(claim.getReceivedConfirmedAt()).isNotBlank();
        assertThat(ItemStatus.isArchived(item.getStatus())).isTrue();
        assertThat(item.getClaimConfirmed()).isTrue();

        verify(custodyLedgerService).appendEvent(eq("found_001"), eq("handoff_verified"), any(), any(), any(), any(), any());
        verify(custodyLedgerService).appendEvent(eq("found_001"), eq("returned"), any(), any(), any(), any(), any());
        verify(recoveryCaseService).markReturned("claim_001", "found_001");
        verify(recoveryPulse).itemReturned(pass);
    }

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
        service.redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin());

        assertThatThrownBy(() -> service.redeem("pass_001", new ReturnPassRedeemRequest(CODE), admin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void expiredPassCannotCompletePickup() {
        ReturnPass pass = pass("pass_expired", "active", CODE, "2026-06-20T12:00:00Z");
        when(returnPasses.findById("pass_expired")).thenReturn(Optional.of(pass));
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service().redeem("pass_expired", new ReturnPassRedeemRequest(CODE), admin()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("xpired");

        assertThat(pass.getStatus()).isEqualTo("expired");
        verify(claims, never()).save(any(Claim.class));
        verify(foundItems, never()).save(any(FoundItem.class));
        verify(recoveryCaseService, never()).markReturned(any(), any());
    }

    @Test
    void redeemRejectsMismatchedOneTimeCode() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));

        assertThatThrownBy(() -> service().redeem("pass_001", new ReturnPassRedeemRequest("000000"), admin()))
                .isInstanceOf(BadRequestException.class);

        assertThat(pass.getStatus()).isEqualTo("active");
        verify(returnPasses, never()).save(any(ReturnPass.class));
    }

    @Test
    void getDeniesUnauthorizedReaderAndHidesOneTimeCode() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin("intruder@pleasantvalley.edu")).thenReturn(false);
        when(authorizationService.resolveEmail("intruder@pleasantvalley.edu")).thenReturn("intruder@pleasantvalley.edu");

        assertThatThrownBy(() -> service().get("pass_001", "intruder@pleasantvalley.edu", authorizationService))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getDeniesUnauthenticatedReaderEvenWhenClaimantEmailIsBlank() {
        ReturnPass pass = pass("pass_blank", "active", CODE, "2026-06-24T12:00:00Z");
        pass.setClaimantEmail(null);
        when(returnPasses.findById("pass_blank")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin(null)).thenReturn(false);
        when(authorizationService.resolveEmail(null)).thenReturn("");

        assertThatThrownBy(() -> service().get("pass_blank", null, authorizationService))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getAllowsClaimantToReadOwnPass() {
        ReturnPass pass = pass("pass_001", "active", CODE, "2026-06-24T12:00:00Z");
        when(returnPasses.findById("pass_001")).thenReturn(Optional.of(pass));
        when(authorizationService.isAdmin(CLAIMANT_EMAIL)).thenReturn(false);
        when(authorizationService.resolveEmail(CLAIMANT_EMAIL)).thenReturn(CLAIMANT_EMAIL);

        ReturnPassResponse response = service().get("pass_001", CLAIMANT_EMAIL, authorizationService);

        assertThat(response.claimId()).isEqualTo("claim_001");
        assertThat(response.oneTimeCode()).isEqualTo(CODE);
    }

    private ReturnPassService service() {
        return new ReturnPassService(
                returnPasses, claims, foundItems, notifications, custodyLedgerService, recoveryCaseService, new FixedClock(), recoveryPulse, completionCleanup);
    }

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

    private Claim claim(String id, String status) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setFoundItemId("found_001");
        claim.setClaimantEmail(CLAIMANT_EMAIL);
        claim.setStatus(status);
        return claim;
    }

    private FoundItem foundItem(String id, String status) {
        FoundItem item = new FoundItem();
        item.setId(id);
        item.setTitle("Blue JanSport Backpack");
        item.setCategory("bags");
        item.setStatus(status);
        return item;
    }

    private AppUser admin() {
        AppUser admin = new AppUser();
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole("admin");
        return admin;
    }

    private static class FixedClock extends ClockService {
        @Override
        public String now() {
            return "2026-06-22T12:00:00Z";
        }
    }
}
