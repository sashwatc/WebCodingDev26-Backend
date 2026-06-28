package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.LostReport;
import com.FBLA.WebCodingDev26Backend.model.RecoveryCase;
import com.FBLA.WebCodingDev26Backend.repository.LostReportRepository;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.RecoveryCaseService;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to recovery cases for the admin recovery dashboard and a user's own cases.
 *
 * <p>Base route: {@code /api/recovery-cases} ({@link RequestMapping}). A "recovery case" is the
 * tracked effort to reunite a lost report with its owner. Staff/admin can browse all cases via
 * the collection endpoints; a lost report's owner can view and refresh the single case derived
 * from their own report.
 *
 * <p>Collaborators: {@link RecoveryCaseService} owns case lookup/derivation; {@link LostReportRepository}
 * is used to verify report ownership; {@link DemoAuthorizationService} resolves caller role/email.
 * Note the deliberate use of 404 (not 403) for denied owner-scoped access (see {@code requireCaseAccess}).
 */
@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {
    // Service that lists, fetches, derives and refreshes recovery cases.
    private final RecoveryCaseService recoveryCases;
    // Lost report store, used to confirm a caller owns the report behind a case.
    private final LostReportRepository lostReports;
    // Resolves caller role/email and enforces staff/admin requirements.
    private final DemoAuthorizationService authorization;

    /** Constructor injection of the case service, lost-report repository and authorization service. */
    public RecoveryCaseController(
            RecoveryCaseService recoveryCases,
            LostReportRepository lostReports,
            DemoAuthorizationService authorization
    ) {
        this.recoveryCases = recoveryCases;
        this.lostReports = lostReports;
        this.authorization = authorization;
    }

    /**
     * GET {@code /api/recovery-cases} — list all recovery cases (admin recovery dashboard).
     *
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with every {@link RecoveryCase}.
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin.
     */
    @GetMapping
    public List<RecoveryCase> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.list();
    }

    /**
     * GET {@code /api/recovery-cases/{id}} — fetch a single recovery case by its id.
     *
     * @param id path variable: the recovery case id.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the requested {@link RecoveryCase} (service throws 404 if absent).
     * @throws RuntimeException 403 Forbidden when the caller is not staff/admin.
     */
    @GetMapping("/{id}")
    public RecoveryCase get(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.get(id);
    }

    /**
     * Staff missions for a recovery case are an optional planning layer the demo
     * backend does not persist. Return an empty list (instead of 404) so the
     * recovery dashboard renders its "no missions" empty state cleanly.
     */
    @GetMapping("/{id}/missions")
    public List<Object> missions(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Staff/admin only, even though the result is always an empty list (see method Javadoc).
        authorization.requireStaffOrAdmin(userEmail);
        return List.of();
    }

    /**
     * GET {@code /api/recovery-cases/lost-reports/{lostReportId}} — view the recovery case for a lost report.
     *
     * <p>The owner of the lost report (or staff) can view its recovery case; the case is opened on
     * first view.
     *
     * @param lostReportId path variable: the lost report whose case to view.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the {@link RecoveryCase}.
     * @throws NotFoundException 404 when the caller is neither staff nor the report's owner, or the
     *         report does not exist — denial is surfaced as 404 to avoid leaking case existence.
     */
    @GetMapping("/lost-reports/{lostReportId}")
    public RecoveryCase byLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorize owner-or-staff access (throws 404 on denial).
        requireCaseAccess(lostReportId, userEmail);
        return recoveryCases.getByLostReport(lostReportId);
    }

    /**
     * POST {@code /api/recovery-cases/lost-reports/{lostReportId}/refresh} — recompute the recovery plan.
     *
     * <p>Recompute the deterministic recovery plan for the caller's own lost report.
     *
     * @param lostReportId path variable: the lost report whose plan to recompute.
     * @param userEmail optional {@code X-Demo-User-Email} header identifying the caller.
     * @return 200 OK with the refreshed {@link RecoveryCase}.
     * @throws NotFoundException 404 when the caller is neither staff nor the report's owner, or the
     *         report does not exist.
     */
    @PostMapping("/lost-reports/{lostReportId}/refresh")
    public RecoveryCase refreshByLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Authorize and capture the resolved caller email to attribute the refresh.
        String email = requireCaseAccess(lostReportId, userEmail);
        return recoveryCases.refreshForLostReport(lostReportId, email);
    }

    /**
     * Allows the lost report's owner or any staff/admin; returns the resolved caller email.
     * Denied access surfaces as 404 (not 403) so public/non-owner viewers — e.g. the item
     * detail page — simply see no recovery case instead of a hard error, and case existence
     * is not leaked.
     */
    private String requireCaseAccess(String lostReportId, String userEmail) {
        // Resolve the caller's email; an anonymous/unknown caller is treated as "case not found".
        String email = authorization.resolveEmail(userEmail);
        if (email == null || email.isBlank()) {
            throw new NotFoundException("Recovery case not found");
        }
        // Staff/admin bypass the ownership check entirely.
        if (authorization.isStaffOrAdmin(userEmail)) {
            return email;
        }
        // Otherwise the caller must own the lost report behind this case.
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        // Case-insensitive comparison of the caller email against the report's contact email.
        String contactEmail = report.getContactEmail() == null ? "" : report.getContactEmail().trim();
        if (!contactEmail.toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))) {
            // Non-owner: report 404 rather than 403 so case existence is not revealed.
            throw new NotFoundException("Recovery case not found");
        }
        return email;
    }
}
