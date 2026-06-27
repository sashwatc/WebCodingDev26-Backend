package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Auto-approves a freshly-filed claim when it corresponds to an admin-merged
 * strong match for the SAME owner + item, then advances it straight into the
 * return process by issuing the pickup claim code.
 *
 * The trigger is deliberately narrow: an admin must have already linked/merged
 * the found item to a lost report (FoundItem.linkedLostReportId set via the
 * "Link Items" decision), and the claimant must be the person who reported that
 * item lost. Any other claim falls through to the normal manual approval path.
 */
@Service
public class StrongMatchAutoApprovalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StrongMatchAutoApprovalService.class);

    private final FoundItemRepository foundItems;
    private final LostReportRepository lostReports;
    private final AdminWorkflowService adminWorkflow;
    private final ReturnPassService returnPassService;

    public StrongMatchAutoApprovalService(
            FoundItemRepository foundItems,
            LostReportRepository lostReports,
            AdminWorkflowService adminWorkflow,
            ReturnPassService returnPassService
    ) {
        this.foundItems = foundItems;
        this.lostReports = lostReports;
        this.adminWorkflow = adminWorkflow;
        this.returnPassService = returnPassService;
    }

    /**
     * Returns the auto-approved claim, or null when the claim is not eligible
     * (the caller then keeps the manually-submitted claim as-is).
     */
    public Claim maybeAutoApprove(Claim claim) {
        if (claim == null || isBlank(claim.getId()) || isBlank(claim.getFoundItemId())) {
            return null;
        }
        // Only act on brand-new, still-pending claims.
        if (!isPending(claim.getStatus())) {
            return null;
        }

        FoundItem item = foundItems.findById(claim.getFoundItemId()).orElse(null);
        if (item == null) {
            return null;
        }
        // The admin "Link Items" / merge sets linkedLostReportId — the pre-confirmed
        // strong-match signal. No link → ordinary claim → manual approval.
        String linkedReportId = item.getLinkedLostReportId();
        if (isBlank(linkedReportId)) {
            return null;
        }

        LostReport report = lostReports.findById(linkedReportId).orElse(null);
        if (report == null) {
            return null;
        }
        // Same owner: the claimant must be whoever reported this item lost.
        if (!sameEmail(claim.getClaimantEmail(), report.getContactEmail())) {
            return null;
        }

        try {
            AppUser system = systemActor();
            Claim approved = adminWorkflow.approveClaim(
                    claim.getId(),
                    system,
                    Map.of("admin_notes", "Auto-approved: pre-confirmed strong match for the reporting owner."));

            // Advance straight into the return process by issuing the claim code,
            // using the claimant's stated availability as the pickup window.
            try {
                String window = valueOrDefault(approved.getPickupAvailability(), "Next school day during office hours");
                returnPassService.create(
                        approved.getId(),
                        new ReturnPassRequest(window, "PVHS Main Office pickup station"),
                        system);
            } catch (RuntimeException passError) {
                LOGGER.warn("Auto-approved claim {} but return-pass issuance failed: {}", claim.getId(), passError.getMessage());
            }
            return approved;
        } catch (RuntimeException error) {
            LOGGER.warn("Auto-approval failed for claim {}: {}", claim.getId(), error.getMessage());
            return null;
        }
    }

    private boolean isPending(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.equals("submitted")
                || normalized.equals("pending_review")
                || normalized.equals("under_review")
                || normalized.equals("need_more_info");
    }

    private AppUser systemActor() {
        AppUser actor = new AppUser();
        actor.setEmail("system-auto-approval@pvhs.demo");
        actor.setRole("admin");
        actor.setFullName("Auto-Approval");
        return actor;
    }

    private boolean sameEmail(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equalsIgnoreCase(b.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
