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
    /** Logger used to record (non-fatal) auto-approval and return-pass failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(StrongMatchAutoApprovalService.class);

    /** Read access to found items — used to look up the claimed item and its admin-set link. */
    private final FoundItemRepository foundItems;
    /** Read access to lost reports — used to resolve the linked report and verify ownership. */
    private final LostReportRepository lostReports;
    /** Collaborator that performs the actual claim approval (with audit/side-effects). */
    private final AdminWorkflowService adminWorkflow;
    /** Collaborator that issues the pickup return pass / claim code once approved. */
    private final ReturnPassService returnPassService;

    /**
     * Constructs the service with its repository and workflow collaborators
     * (wired by Spring's dependency injection).
     *
     * @param foundItems        repository for looking up the claimed found item
     * @param lostReports       repository for resolving the linked lost report
     * @param adminWorkflow     service that approves the claim as a system actor
     * @param returnPassService service that issues the pickup pass after approval
     */
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
     * Evaluates a freshly-filed claim and, if it matches an admin pre-confirmed
     * strong match for the reporting owner, approves it automatically and issues
     * the pickup return pass.
     *
     * <p>Eligibility gate (ALL must hold, otherwise the claim is left for manual review):
     * <ul>
     *   <li>claim is non-null and has both an id and a found-item id;</li>
     *   <li>claim status is still pending (see {@link #isPending});</li>
     *   <li>the referenced found item exists and carries a {@code linkedLostReportId}
     *       (the admin "Link Items"/merge signal);</li>
     *   <li>the linked lost report exists;</li>
     *   <li>the claimant's email matches the report's contact email (same owner).</li>
     * </ul>
     *
     * <p>Side effects on success: the claim is approved via {@link AdminWorkflowService}
     * acting as a synthetic system admin, and a {@link ReturnPassService} pass is created.
     * Return-pass issuance failures are logged but do NOT undo the approval.
     *
     * @param claim the newly-submitted claim to evaluate
     * @return the approved {@link Claim} when auto-approval succeeded, or {@code null}
     *         when the claim is ineligible or approval failed (caller keeps it as-is)
     */
    public Claim maybeAutoApprove(Claim claim) {
        // Guard: need a real claim with the identifiers required to look things up.
        if (claim == null || isBlank(claim.getId()) || isBlank(claim.getFoundItemId())) {
            return null;
        }
        // Only act on brand-new, still-pending claims.
        if (!isPending(claim.getStatus())) {
            return null;
        }

        // Resolve the found item the claim targets; bail if it no longer exists.
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

        // Resolve the lost report the admin linked this item to; bail if missing.
        LostReport report = lostReports.findById(linkedReportId).orElse(null);
        if (report == null) {
            return null;
        }
        // Same owner: the claimant must be whoever reported this item lost.
        if (!sameEmail(claim.getClaimantEmail(), report.getContactEmail())) {
            return null;
        }

        try {
            // Approve as a synthetic admin so the audit trail records who/why.
            AppUser system = systemActor();
            Claim approved = adminWorkflow.approveClaim(
                    claim.getId(),
                    system,
                    Map.of("admin_notes", "Auto-approved: pre-confirmed strong match for the reporting owner."));

            // Advance straight into the return process by issuing the claim code,
            // using the claimant's stated availability as the pickup window.
            try {
                // Fall back to a sensible default window if the claimant gave none.
                String window = valueOrDefault(approved.getPickupAvailability(), "Next school day during office hours");
                returnPassService.create(
                        approved.getId(),
                        new ReturnPassRequest(window, "PVHS Main Office pickup station"),
                        system);
            } catch (RuntimeException passError) {
                // Non-fatal: the claim is still approved even if pass issuance fails.
                LOGGER.warn("Auto-approved claim {} but return-pass issuance failed: {}", claim.getId(), passError.getMessage());
            }
            return approved;
        } catch (RuntimeException error) {
            // Approval failed unexpectedly — log and fall back to manual review.
            LOGGER.warn("Auto-approval failed for claim {}: {}", claim.getId(), error.getMessage());
            return null;
        }
    }

    /**
     * Determines whether a claim status counts as "still pending" (i.e. not yet
     * decided), so auto-approval only ever acts on undecided claims.
     *
     * @param status the raw claim status (may be null/blank)
     * @return true if blank or one of the pre-decision statuses
     *         (submitted / pending_review / under_review / need_more_info)
     */
    private boolean isPending(String status) {
        // Normalize to lower-case/trimmed so comparisons are case-insensitive.
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.equals("submitted")
                || normalized.equals("pending_review")
                || normalized.equals("under_review")
                || normalized.equals("need_more_info");
    }

    /**
     * Builds the synthetic admin user that the auto-approval acts as, so the
     * approval is attributed to the system rather than a real staff member.
     *
     * @return an in-memory {@link AppUser} with the admin role and a system email
     */
    private AppUser systemActor() {
        AppUser actor = new AppUser();
        actor.setEmail("system-auto-approval@pvhs.demo");
        actor.setRole("admin");
        actor.setFullName("Auto-Approval");
        return actor;
    }

    /**
     * Case-insensitive, null/blank-safe email equality check.
     *
     * @param a first email
     * @param b second email
     * @return true only if both are non-blank and equal ignoring case/whitespace
     */
    private boolean sameEmail(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Null/blank string test helper.
     *
     * @param value the string to check
     * @return true if the value is null or contains only whitespace
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns {@code value} unless it is blank, in which case {@code fallback} is used.
     *
     * @param value    the preferred value
     * @param fallback the default used when {@code value} is blank
     * @return value when present, otherwise fallback
     */
    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
