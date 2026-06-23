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
import com.FBLA.WebCodingDev26Backend.model.Notification;
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
import org.springframework.stereotype.Service;

@Service
public class ReturnPassService {
    private final ReturnPassRepository returnPasses;
    private final ClaimRepository claims;
    private final FoundItemRepository foundItems;
    private final NotificationRepository notifications;
    private final CustodyLedgerService custodyLedgerService;
    private final RecoveryCaseService recoveryCaseService;
    private final ClockService clock;
    private final SecureRandom random = new SecureRandom();

    public ReturnPassService(
            ReturnPassRepository returnPasses,
            ClaimRepository claims,
            FoundItemRepository foundItems,
            NotificationRepository notifications,
            CustodyLedgerService custodyLedgerService,
            RecoveryCaseService recoveryCaseService,
            ClockService clock
    ) {
        this.returnPasses = returnPasses;
        this.claims = claims;
        this.foundItems = foundItems;
        this.notifications = notifications;
        this.custodyLedgerService = custodyLedgerService;
        this.recoveryCaseService = recoveryCaseService;
        this.clock = clock;
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
        pass.setOneTimeCode(generateCode());
        pass.setToken(generateToken());
        pass.setExpiresAt(Instant.parse(now).plus(2, ChronoUnit.DAYS).toString());
        pass.setCreatedDate(now);
        pass.setUpdatedDate(now);
        ReturnPass saved = returnPasses.save(pass);

        custodyLedgerService.appendEvent(item.getId(), "pickup_ready", admin.getEmail(), admin.getRole(), pass.getPickupLocation(), "Return Pass issued for approved claim.", null);
        recoveryCaseService.markPickupReady(claim.getId(), item.getId());
        createNotification(claim.getClaimantEmail(), "Your item is ready for pickup", "A Return Pass is active for your approved claim.", "return_pass_ready", "/PickupPass?id=" + saved.getId(), item.getId());
        return ReturnPassResponse.from(saved);
    }

    public ReturnPassResponse get(String id, String userEmail, DemoAuthorizationService authorizationService) {
        ReturnPass pass = returnPasses.findById(id).orElseThrow(() -> new NotFoundException("Return Pass not found"));
        String normalized = normalize(userEmail);
        if (!authorizationService.isAdmin(userEmail) && !normalize(pass.getClaimantEmail()).equals(normalized)) {
            throw new ForbiddenException("Return Pass access is restricted to the claimant or an admin.");
        }
        return ReturnPassResponse.from(pass);
    }

    public ReturnPassVerifyResponse verify(ReturnPassVerifyRequest request) {
        ReturnPass pass = returnPasses.findByOneTimeCode(request.oneTimeCode().trim())
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
        if (!pass.getOneTimeCode().equals(request.oneTimeCode().trim())) {
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
        createNotification(claim.getClaimantEmail(), "Item returned", "Your pickup was completed at the PVHS pickup station.", "item_returned", "/UserDashboard", item.getId());
        return ReturnPassResponse.from(saved);
    }

    private boolean isExpired(ReturnPass pass) {
        try {
            return Instant.parse(pass.getExpiresAt()).isBefore(Instant.parse(clock.now()));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private void createNotification(String email, String title, String message, String type, String link, String itemId) {
        Notification notification = new Notification();
        notification.setId("notif_" + shortId());
        notification.setUserEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRelatedItemId(itemId);
        notification.setIsRead(false);
        notification.setCreatedDate(clock.now());
        notification.setUpdatedDate(clock.now());
        notifications.save(notification);
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
