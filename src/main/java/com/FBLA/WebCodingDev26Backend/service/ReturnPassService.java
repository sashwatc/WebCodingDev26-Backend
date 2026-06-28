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

/**
 * Owns the secure item-handoff workflow built around a {@link ReturnPass} — the one-time-code
 * "pickup pass" issued once a claim is approved and redeemed when staff hand the item back.
 *
 * <p>Responsibilities and lifecycle:
 * <ul>
 *   <li>{@link #create}: issue a pass for an approved claim on a verified item, generating a
 *       6-digit one-time code (stored both as a bcrypt hash and, for retrieval, in plaintext), a
 *       random URL-safe token, and a 2-day expiry; links the pass to the claim and advances the
 *       recovery case to pickup-ready.</li>
 *   <li>{@link #get}: access-controlled read restricted to the claimant or an admin.</li>
 *   <li>{@link #verify}: non-mutating validity check of a code at the pickup station.</li>
 *   <li>{@link #redeem} / {@link #redeemByCode}: complete the handoff — mark the pass redeemed,
 *       the claim completed, and the item archived, and append custody-ledger events.</li>
 *   <li>{@link #sendPickupReminder}: re-notify the claimant of a still-active pass.</li>
 * </ul>
 *
 * <p>Collaborators: {@link ReturnPassRepository}, {@link ClaimRepository},
 * {@link FoundItemRepository}, {@link CustodyLedgerService} (chain-of-custody audit),
 * {@link RecoveryCaseService} (case status), {@link ClockService}, optional
 * {@link RecoveryPulseDispatcher} (notifications), and optional {@link CompletionCleanupService}.
 */
@Service
public class ReturnPassService {
    /** Persistence for return passes. */
    private final ReturnPassRepository returnPasses;
    /** Persistence for claims (the pass is created for an approved claim and completes it on redeem). */
    private final ClaimRepository claims;
    /** Persistence for found items (verified before issue, archived on redeem). */
    private final FoundItemRepository foundItems;
    /** Records chain-of-custody events (pickup-ready, handoff-verified, returned). */
    private final CustodyLedgerService custodyLedgerService;
    /** Keeps the related recovery case's status in sync with the pass lifecycle. */
    private final RecoveryCaseService recoveryCaseService;
    /** Timestamp provider (injectable for deterministic tests). */
    private final ClockService clock;
    /** Optional notification dispatcher; null disables pass-related notifications. */
    private final RecoveryPulseDispatcher recoveryPulse;
    /** Optional cleanup service for explicit admin "remove completed item" actions (not auto-run on redeem). */
    private final CompletionCleanupService completionCleanup;
    /** Cryptographically strong RNG used to mint one-time codes and tokens. */
    private final SecureRandom random = new SecureRandom();
    /** Hasher used to store the one-time code as a bcrypt hash and to verify codes on redeem. */
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /**
     * Convenience constructor without the notification dispatcher or cleanup service. Delegates to
     * the primary constructor with both optional collaborators null.
     */
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

    /**
     * Primary (Spring-wired) constructor. Stores all collaborators. The {@code notifications}
     * repository parameter is accepted for wiring compatibility but not retained. The recovery-pulse
     * dispatcher and completion-cleanup service are optional (may be null).
     */
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

    /**
     * Issues a new Return Pass for an approved claim.
     *
     * <p>Preconditions (each violation throws {@link ConflictException} unless noted):
     * <ul>
     *   <li>The claim must exist ({@link NotFoundException}) and be "approved".</li>
     *   <li>The found item must exist ({@link NotFoundException}) and must not already be archived.</li>
     *   <li>The item must be "claimed" or canonically VERIFIED.</li>
     *   <li>No other active pass may already exist for the claim.</li>
     * </ul>
     *
     * <p>On success it builds a pass with a generated id, default-or-supplied pickup window/location,
     * an "active" status, a fresh 6-digit one-time code (stored as a bcrypt hash and persisted in
     * plaintext for the access-controlled GET), a random token, and a 2-day expiry; inherits the demo
     * flag from the claim/item. Side effects: saves the pass, links it back onto the claim, appends a
     * "pickup_ready" custody event, advances the recovery case to pickup-ready, and (if configured)
     * notifies the claimant.
     *
     * @param claimId id of the approved claim
     * @param request optional pickup window/location overrides
     * @param admin   the staff member issuing the pass (recorded in the custody ledger)
     * @return the created pass as a response DTO
     */
    public ReturnPassResponse create(String claimId, ReturnPassRequest request, AppUser admin) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        // A pass is only meaningful for a claim staff have already approved.
        if (!"approved".equalsIgnoreCase(claim.getStatus())) {
            throw new ConflictException("Return Pass can only be created for an approved claim.");
        }

        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        // Already-returned (archived) items cannot be handed out again.
        if (ItemStatus.isArchived(item.getStatus())) {
            throw new ConflictException("Returned items cannot receive a new Return Pass.");
        }
        // The item must have reached a claimed/verified state before a pickup pass can be issued.
        if (!"claimed".equalsIgnoreCase(item.getStatus()) && !ItemStatus.VERIFIED.equals(ItemStatus.canonical(item.getStatus()))) {
            throw new ConflictException("Found item must be verified before creating a Return Pass.");
        }

        // Prevent duplicate active passes for the same claim.
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
        // Apply caller-provided pickup details or sensible PVHS defaults.
        pass.setPickupWindow(valueOrDefault(request.pickupWindow(), "Next school day during office hours"));
        pass.setPickupLocation(valueOrDefault(request.pickupLocation(), "PVHS Main Office pickup station"));
        pass.setStatus("active");
        // Mint the one-time code: store the bcrypt hash for verification...
        String plainCode = generateCode();
        pass.setPinHash(bcrypt.encode(plainCode));
        pass.setOneTimeCode(plainCode); // persisted so the claimant can read it via the access-controlled GET
        // Random URL-safe token for link-based access; pass expires 2 days out.
        pass.setToken(generateToken());
        pass.setExpiresAt(Instant.parse(now).plus(2, ChronoUnit.DAYS).toString());
        pass.setCreatedDate(now);
        pass.setUpdatedDate(now);
        // Demo if either the claim or the item is demo data.
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

        // Record the pickup-ready custody event, advance the recovery case, and notify the claimant.
        custodyLedgerService.appendEvent(item.getId(), "pickup_ready", admin.getEmail(), admin.getRole(), pass.getPickupLocation(), "Return Pass issued for approved claim.", null);
        recoveryCaseService.markPickupReady(claim.getId(), item.getId());
        if (recoveryPulse != null) {
            recoveryPulse.returnPassReady(saved);
        }
        return ReturnPassResponse.from(saved);
    }

    /**
     * Returns a pass by id, enforcing access control: admins may always read it; otherwise the
     * caller's verified email must match the pass's claimant email.
     *
     * @param id                   id of the pass
     * @param userEmail            the requesting user's (claimed) email
     * @param authorizationService resolver used to confirm admin status and verify the caller's email
     * @return the pass as a response DTO
     * @throws NotFoundException  if the pass does not exist
     * @throws ForbiddenException if the caller is neither an admin nor the claimant
     */
    public ReturnPassResponse get(String id, String userEmail, DemoAuthorizationService authorizationService) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        // Admins bypass the claimant check.
        if (authorizationService.isAdmin(userEmail)) {
            return ReturnPassResponse.from(pass);
        }
        // Non-admins must prove they are the claimant (verified email must match).
        String verifiedEmail = normalize(authorizationService.resolveEmail(userEmail));
        String claimantEmail = normalize(pass.getClaimantEmail());
        if (verifiedEmail.isBlank() || !claimantEmail.equals(verifiedEmail)) {
            throw new ForbiddenException("Return Pass access is restricted to the claimant or an admin.");
        }
        return ReturnPassResponse.from(pass);
    }

    /**
     * Non-mutating validity check of a one-time code at the pickup station.
     *
     * <p>Looks up the pass by code, then returns a result describing whether it is usable: a pass
     * that is not "active" or that has expired yields {@code valid=false} with an explanation;
     * otherwise it returns {@code valid=true} along with the found-item and claim ids. Unlike
     * {@link #redeem}, this does not change any state (an expired pass is reported, not persisted).
     *
     * @param request carries the one-time code
     * @return a verification result
     * @throws NotFoundException if no pass matches the code
     */
    public ReturnPassVerifyResponse verify(ReturnPassVerifyRequest request) {
        String code = request.oneTimeCode().trim();
        // Try hash-based lookup first; fall back to legacy plaintext for seeded demo data
        ReturnPass pass = findByCode(code)
                .orElseThrow(() -> new NotFoundException("Return Pass not found"));
        // Only active passes are usable.
        if (!"active".equalsIgnoreCase(pass.getStatus())) {
            return new ReturnPassVerifyResponse(false, pass.getId(), pass.getStatus(), "", "", "Return Pass is not active.");
        }
        // Past expiry is invalid (reported, not persisted here).
        if (isExpired(pass)) {
            return new ReturnPassVerifyResponse(false, pass.getId(), "expired", "", "", "Return Pass is expired.");
        }
        return new ReturnPassVerifyResponse(true, pass.getId(), pass.getStatus(), pass.getFoundItemId(), pass.getClaimId(), "Return Pass is valid.");
    }

    /**
     * Redeems a pass by id, completing the secure handoff of the item to its owner.
     *
     * <p>Validation: the supplied code must match (bcrypt hash, or legacy plaintext for seeded data);
     * the pass must be "active"; and it must not be expired (an expired pass is flipped to "expired"
     * and persisted before failing). It then re-loads the claim and item and guards against a pickup
     * that was already completed (item archived or claim completed).
     *
     * <p>State transitions on success: the pass becomes "redeemed" (stamping redeemed-at/redeemed-by);
     * the claim becomes "completed" (stamping received-confirmed-at); the item becomes ARCHIVED with
     * its claim-confirmed flag set. It then appends "handoff_verified" and "returned" custody events,
     * marks the recovery case returned, and (if configured) notifies the claimant.
     *
     * @param id      id of the pass to redeem
     * @param request carries the one-time code
     * @param admin   the staff member performing the handoff (recorded in the custody ledger)
     * @return the redeemed pass as a response DTO
     * @throws NotFoundException   if the pass, claim, or item is missing
     * @throws BadRequestException if the code does not match
     * @throws ConflictException   if the pass is not active/expired, or the pickup was already completed
     */
    public ReturnPassResponse redeem(String id, ReturnPassRedeemRequest request, AppUser admin) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        String code = request.oneTimeCode().trim();
        // Verify the code against the bcrypt hash, or the legacy plaintext code for seeded passes.
        boolean codeMatches = (pass.getPinHash() != null)
                ? bcrypt.matches(code, pass.getPinHash())
                : code.equals(pass.getOneTimeCode()); // legacy plaintext fallback for seeded data
        if (!codeMatches) {
            throw new BadRequestException("One-time code does not match this Return Pass.");
        }
        // Only an active pass can be redeemed.
        if (!"active".equalsIgnoreCase(pass.getStatus())) {
            throw new ConflictException("Return Pass cannot be redeemed because it is " + pass.getStatus() + ".");
        }
        // Expired passes are marked expired (persisted) and then rejected.
        if (isExpired(pass)) {
            pass.setStatus("expired");
            pass.setUpdatedDate(clock.now());
            returnPasses.save(pass);
            throw new ConflictException("Expired Return Pass cannot be redeemed.");
        }

        Claim claim = claims.findById(pass.getClaimId()).orElseThrow(() -> new NotFoundException("Claim not found"));
        FoundItem item = foundItems.findById(pass.getFoundItemId()).orElseThrow(() -> new NotFoundException("Found item not found"));
        // Idempotency guard: don't double-complete a pickup.
        if (ItemStatus.isArchived(item.getStatus()) || "completed".equalsIgnoreCase(claim.getStatus())) {
            throw new ConflictException("This pickup has already been completed.");
        }

        String now = clock.now();
        // Mark the pass redeemed, recording who redeemed it and when.
        pass.setStatus("redeemed");
        pass.setRedeemedAt(now);
        pass.setRedeemedBy(admin.getEmail());
        pass.setUpdatedDate(now);
        ReturnPass saved = returnPasses.save(pass);

        // Complete the claim.
        claim.setStatus("completed");
        claim.setReceivedConfirmedAt(now);
        claim.setUpdatedDate(now);
        claims.save(claim);

        // Archive the item as a permanent completed-recovery record.
        item.setStatus(ItemStatus.ARCHIVED);
        item.setClaimConfirmed(true);
        item.setClaimConfirmedAt(now);
        item.setUpdatedDate(now);
        foundItems.save(item);

        // Append the two terminal custody-ledger events and advance the recovery case to returned.
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

    /**
     * Convenience redemption entry point that locates the pass by its one-time code and delegates to
     * {@link #redeem(String, ReturnPassRedeemRequest, AppUser)} (which re-validates the code).
     *
     * @param request carries the one-time code
     * @param admin   the staff member performing the handoff
     * @return the redeemed pass as a response DTO
     * @throws NotFoundException if no pass matches the code
     */
    public ReturnPassResponse redeemByCode(ReturnPassRedeemRequest request, AppUser admin) {
        String code = request.oneTimeCode().trim();
        ReturnPass pass = findByCode(code)
                .orElseThrow(() -> new NotFoundException("Return Pass not found for the provided code."));
        return redeem(pass.getId(), request, admin);
    }

    /**
     * Re-sends the pickup reminder notification for an active pass.
     *
     * @param id    id of the pass
     * @param admin the staff member triggering the reminder
     * @return the pass as a response DTO
     * @throws NotFoundException if the pass does not exist
     * @throws ConflictException if the pass is not active
     */
    public ReturnPassResponse sendPickupReminder(String id, AppUser admin) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        // Reminders make sense only while the pass is still active.
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
        // Hash-based scan for passes created after BCrypt migration: bcrypt-compare the code against
        // every pass that has a stored hash and return the first match.
        return returnPasses.findAll().stream()
                .filter(p -> p.getPinHash() != null && bcrypt.matches(code, p.getPinHash()))
                .findFirst();
    }

    /**
     * Returns true if the pass is past its expiry instant relative to the current clock. Treats any
     * parse failure (e.g. a missing/malformed expiry) as expired, failing closed for safety.
     */
    private boolean isExpired(ReturnPass pass) {
        try {
            return Instant.parse(pass.getExpiresAt()).isBefore(Instant.parse(clock.now()));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    /** Generates a zero-padded random 6-digit one-time code (000000–999999). */
    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    /** Generates a random 256-bit URL-safe, unpadded Base64 token for link-based pass access. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Generates a 10-character lowercase id fragment from a random UUID (dashes removed). */
    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** Returns {@code value} if non-null and non-blank, otherwise {@code fallback}. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Lowercases and trims a string for case-insensitive email comparison; null becomes empty. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
