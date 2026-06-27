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

/** Read access to recovery cases for the admin recovery dashboard and a user's own cases. */
@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {
    private final RecoveryCaseService recoveryCases;
    private final LostReportRepository lostReports;
    private final DemoAuthorizationService authorization;

    public RecoveryCaseController(
            RecoveryCaseService recoveryCases,
            LostReportRepository lostReports,
            DemoAuthorizationService authorization
    ) {
        this.recoveryCases = recoveryCases;
        this.lostReports = lostReports;
        this.authorization = authorization;
    }

    @GetMapping
    public List<RecoveryCase> list(@RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.list();
    }

    @GetMapping("/{id}")
    public RecoveryCase get(@PathVariable String id, @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        authorization.requireStaffOrAdmin(userEmail);
        return recoveryCases.get(id);
    }

    /** The owner of a lost report (or staff) can view its recovery case; the case is opened on first view. */
    @GetMapping("/lost-reports/{lostReportId}")
    public RecoveryCase byLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        requireCaseAccess(lostReportId, userEmail);
        return recoveryCases.getByLostReport(lostReportId);
    }

    /** Recompute the deterministic recovery plan for the caller's own lost report. */
    @PostMapping("/lost-reports/{lostReportId}/refresh")
    public RecoveryCase refreshByLostReport(
            @PathVariable String lostReportId,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
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
        String email = authorization.resolveEmail(userEmail);
        if (email == null || email.isBlank()) {
            throw new NotFoundException("Recovery case not found");
        }
        if (authorization.isStaffOrAdmin(userEmail)) {
            return email;
        }
        LostReport report = lostReports.findById(lostReportId)
                .orElseThrow(() -> new NotFoundException("Lost report not found"));
        String contactEmail = report.getContactEmail() == null ? "" : report.getContactEmail().trim();
        if (!contactEmail.toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))) {
            throw new NotFoundException("Recovery case not found");
        }
        return email;
    }
}
