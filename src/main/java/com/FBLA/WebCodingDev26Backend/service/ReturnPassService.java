package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRedeemRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassResponse;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyRequest;
import com.FBLA.WebCodingDev26Backend.dto.ReturnPassVerifyResponse;
import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.ConflictException;
import com.FBLA.WebCodingDev26Backend.exception.ForbiddenException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ReturnPassService {
    private final ReturnPassRepository returnPasses;
    private final ClaimRepository claims;
    private final FoundItemRepository foundItems;
    private final CustodyLedgerService custodyLedgerService;
    private final RecoveryCaseService recoveryCaseService;
    private final ClockService clock;
    private final RecoveryPulseDispatcher recoveryPulse;
    private final CompletionCleanupService completionCleanup;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public ReturnPassService(
            ReturnPassRepository returnPasses,
            ClaimRepository claims,
            FoundItemRepository foundItems,
            NotificationRepository notifications,
            CustodyLedgerService custodyLedgerService,
            RecoveryCaseService recoveryCaseService,
            ClockService clock
    ) {
        this(returnPasses, claims, foundItems, notifications, custodyLedgerService, recoveryCaseService, clock, null, null);
    }

    @Autowired
    public ReturnPassService(
            ReturnPassRepository returnPasses,
            ClaimRepository claims,
            FoundItemRepository foundItems,
            NotificationRepository notifications,
            CustodyLedgerService custodyLedgerService,
            RecoveryCaseService recoveryCaseService,
            ClockService clock,
            RecoveryPulseDispatcher recoveryPulse,
            CompletionCleanupService completionCleanup
    ) {
        this.returnPasses = returnPasses;
        this.claims = claims;
        this.foundItems = foundItems;
        this.custodyLedgerService = custodyLedgerService;
        this.recoveryCaseService = recoveryCaseService;
        this.clock = clock;
        this.recoveryPulse = recoveryPulse;
        this.completionCleanup = completionCleanup;
    }

    public ReturnPassResponse create(String claimId, ReturnPassRequest request, AppUser admin) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!"approved".equalsIgnoreCase(claim.getStatus())) {
            throw new ConflictException("Return Pass can only be created for an approved claim.");
        }

        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        if (ItemStatus.isArchived(item.getStatus())) {
            throw new ConflictException("Returned items cannot receive a new Return Pass.");
        }
        if (!"claimed".equalsIgnoreCase(item.getStatus()) && !ItemStatus.VERIFIED.equals(ItemStatus.canonical(item.getStatus()))) {
            throw new ConflictException("Found item must be verified before creating a Return Pass.");
        }

        returnPasses.findByClaimId(claimId).stream()
                .filter(pass -> "active".equalsIgnoreCase(pass.getStatus()))
                .findFirst()
                .ifPresent(pass -> {
                    throw new ConflictException("An active Return Pass already exists for this claim.");
                });

        String now = clock.now();
        ReturnPass pass = new ReturnPass();
        pass.setId("pass_" + shortId());
        pass.setClaimId(claim.getId());
        pass.setFoundItemId(item.getId());
        pass.setClaimantEmail(claim.getClaimantEmail());
        pass.setPickupWindow(valueOrDefault(request.pickupWindow(), "Next school day during office hours"));
        pass.setPickupLocation(valueOrDefault(request.pickupLocation(), "PVHS Main Office pickup station"));
        pass.setStatus("active");
        String plainCode = generateCode();
        pass.setPinHash(bcrypt.encode(plainCode));
        pass.setOneTimeCode(plainCode); // persisted so the claimant can read it via the access-controlled GET
        pass.setToken(generateToken());
        pass.setExpiresAt(Instant.parse(now).plus(2, ChronoUnit.DAYS).toString());
        pass.setCreatedDate(now);
        pass.setUpdatedDate(now);
        pass.setIsDemo(Boolean.TRUE.equals(claim.getIsDemo()) || Boolean.TRUE.equals(item.getIsDemo()));
        // Persist the plaintext one-time code (alongside the bcrypt pinHash) so the
        // claimant can retrieve it later via the access-controlled GET — the Pickup Pass
        // page renders the code/QR from that GET, not from this create response.
        ReturnPass saved = returnPasses.save(pass);

        // Link the claim to its pass so user-facing views can surface the pickup
        // pass/approval status directly (by claim id) without relying on notifications.
        claim.setReturnPassId(saved.getId());
        claim.setUpdatedDate(now);
        claims.save(claim);

        custodyLedgerService.appendEvent(item.getId(), "pickup_ready", admin.getEmail(), admin.getRole(), pass.getPickupLocation(), "Return Pass issued for approved claim.", null);
        recoveryCaseService.markPickupReady(claim.getId(), item.getId());
        if (recoveryPulse != null) {
            recoveryPulse.returnPassReady(saved);
        }
        return ReturnPassResponse.from(saved);
    }

    public ReturnPassResponse get(String id, String userEmail, DemoAuthorizationService authorizationService) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        if (authorizationService.isAdmin(userEmail)) {
            return ReturnPassResponse.from(pass);
        }
        String verifiedEmail = normalize(authorizationService.resolveEmail(userEmail));
        String claimantEmail = normalize(pass.getClaimantEmail());
        if (verifiedEmail.isBlank() || !claimantEmail.equals(verifiedEmail)) {
            throw new ForbiddenException("Return Pass access is restricted to the claimant or an admin.");
        }
        return ReturnPassResponse.from(pass);
    }

    public ReturnPassVerifyResponse verify(ReturnPassVerifyRequest request) {
        String code = request.oneTimeCode().trim();
        // Try hash-based lookup first; fall back to legacy plaintext for seeded demo data
        ReturnPass pass = findByCode(code)
                .orElseThrow(() -> new NotFoundException("Return Pass not found"));
        if (!"active".equalsIgnoreCase(pass.getStatus())) {
            return new ReturnPassVerifyResponse(false, pass.getId(), pass.getStatus(), "", "", "Return Pass is not active.");
        }
        if (isExpired(pass)) {
            return new ReturnPassVerifyResponse(false, pass.getId(), "expired", "", "", "Return Pass is expired.");
        }
        return new ReturnPassVerifyResponse(true, pass.getId(), pass.getStatus(), pass.getFoundItemId(), pass.getClaimId(), "Return Pass is valid.");
    }

    public ReturnPassResponse redeem(String id, ReturnPassRedeemRequest request, AppUser admin) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        String code = request.oneTimeCode().trim();
        boolean codeMatches = (pass.getPinHash() != null)
                ? bcrypt.matches(code, pass.getPinHash())
                : code.equals(pass.getOneTimeCode()); // legacy plaintext fallback for seeded data
        if (!codeMatches) {
            throw new BadRequestException("One-time code does not match this Return Pass.");
        }
        if (!"active".equalsIgnoreCase(pass.getStatus())) {
            throw new ConflictException("Return Pass cannot be redeemed because it is " + pass.getStatus() + ".");
        }
        if (isExpired(pass)) {
            pass.setStatus("expired");
            pass.setUpdatedDate(clock.now());
            returnPasses.save(pass);
            throw new ConflictException("Expired Return Pass cannot be redeemed.");
        }

        Claim claim = claims.findById(pass.getClaimId()).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(pass.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        if (ItemStatus.isArchived(item.getStatus()) || "completed".equalsIgnoreCase(claim.getStatus())) {
            throw new ConflictException("This pickup has already been completed.");
        }

        String now = clock.now();
        pass.setStatus("redeemed");
        pass.setRedeemedAt(now);
        pass.setRedeemedBy(admin.getEmail());
        pass.setUpdatedDate(now);
        ReturnPass saved = returnPasses.save(pass);

        claim.setStatus("completed");
        claim.setReceivedConfirmedAt(now);
        claim.setUpdatedDate(now);
        claims.save(claim);

        item.setStatus(ItemStatus.ARCHIVED);
        item.setClaimConfirmed(true);
        item.setClaimConfirmedAt(now);
        item.setUpdatedDate(now);
        foundItems.save(item);

        custodyLedgerService.appendEvent(item.getId(), "handoff_verified", admin.getEmail(), admin.getRole(), pass.getPickupLocation(), "Pickup station verified the one-time code.", null);
        custodyLedgerService.appendEvent(item.getId(), "returned", admin.getEmail(), admin.getRole(), pass.getPickupLocation(), "Item returned to verified claimant.", null);
        recoveryCaseService.markReturned(claim.getId(), item.getId());
        if (recoveryPulse != null) {
            recoveryPulse.itemReturned(saved);
        }

        // Redemption leaves the item in the ARCHIVED state as a persistent
        // completed recovery record (still visible in history, the admin dashboard,
        // and the custody ledger). Cascade cleanup is NOT run automatically here —
        // it is reserved for an explicit admin "remove completed item" action via
        // CompletionCleanupService.purgeCompletedItem (see ClaimController /
        // AdminWorkflowService), so the final archived record persists after pickup.
        return ReturnPassResponse.from(saved);
    }

    public ReturnPassResponse redeemByCode(ReturnPassRedeemRequest request, AppUser admin) {
        String code = request.oneTimeCode().trim();
        ReturnPass pass = findByCode(code)
                .orElseThrow(() -> new NotFoundException("Return Pass not found for the provided code."));
        return redeem(pass.getId(), request, admin);
    }

    public ReturnPassResponse sendPickupReminder(String id, AppUser admin) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        if (!"active".equalsIgnoreCase(pass.getStatus())) {
            throw new ConflictException("Pickup reminders can only be sent for active Return Passes.");
        }
        if (recoveryPulse != null) {
            recoveryPulse.pickupReminder(pass);
        }
        return ReturnPassResponse.from(pass);
    }

    /**
     * Finds a ReturnPass by code using hash-based matching.
     * Falls back to legacy plaintext lookup for seeded demo data that has no pinHash.
     */
    private java.util.Optional<ReturnPass> findByCode(String code) {
        // Plaintext fallback for seeded demo data (no pinHash stored)
        java.util.Optional<ReturnPass> byPlaintext = returnPasses.findByOneTimeCode(code);
        if (byPlaintext.isPresent()) {
            return byPlaintext;
        }
        // Hash-based scan for passes created after BCrypt migration
        return returnPasses.findAll().stream()
                .filter(p -> p.getPinHash() != null && bcrypt.matches(code, p.getPinHash()))
                .findFirst();
    }

    private boolean isExpired(ReturnPass pass) {
        try {
            return Instant.parse(pass.getExpiresAt()).isBefore(Instant.parse(clock.now()));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
