package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.SupportTicket;
import com.FBLA.WebCodingDev26Backend.service.DemoAuthorizationService;
import com.FBLA.WebCodingDev26Backend.service.SupportTicketService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the help-desk / support-ticket feature.
 *
 * <p>Unlike the other controllers here, this one has no class-level {@code @RequestMapping}; each
 * handler declares its own absolute path. It serves two route families: public ticket submission
 * under {@code /api/support/...} and staff management under {@code /api/staff/support/...}. Returns
 * JSON.
 *
 * <p>Authorization: ticket creation is anonymous-friendly (no sign-in required); ticket lookup and
 * all staff endpoints require a staff/admin caller via the demo {@code X-Demo-User-Email} header.
 *
 * <p>Collaborators: {@link SupportTicketService} (ticket + case-message logic) and
 * {@link DemoAuthorizationService} (caller resolution / authorization).
 */
@RestController // JSON REST controller (no shared base path; paths are declared per-method)
public class SupportTicketController {
    /** Encapsulates ticket creation, lookup, staff updates, and reply/case-message handling. */
    private final SupportTicketService service;
    /** Resolves the caller from the demo header and enforces staff/admin access where required. */
    private final DemoAuthorizationService authorizationService;

    /** Constructor injection of the service and authorization collaborators. */
    public SupportTicketController(SupportTicketService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    // ─── Public endpoints ──────────────────────────────────────────────────────

    /**
     * POST {@code /api/support/tickets} — submit a new support ticket (sign-in optional).
     *
     * <p>If the caller is signed in, the submitter's email/name are taken from their account;
     * otherwise they fall back to the {@code email}/{@code name} fields in the body (anonymous
     * submission). Other body fields: {@code category} (default {@code "general"}),
     * {@code subject} (required), {@code message} (required), and an optional linked item id
     * ({@code linkedItemId} or {@code linked_item_id}).
     *
     * @param body loosely-typed request body (see above).
     * @param userEmail optional {@code X-Demo-User-Email} header; used only to prefill submitter info.
     * @return 201 CREATED with {@code {ticketNumber, id, status}} for the new ticket.
     * @throws BadRequestException (400) if {@code subject} or {@code message} is blank.
     */
    @PostMapping("/api/support/tickets")
    @ResponseStatus(HttpStatus.CREATED) // success returns HTTP 201
    public Map<String, Object> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        // Auth is optional — allow anonymous submissions
        AppUser user = authorizationService.currentUser(userEmail);
        String submitterEmail = user != null ? user.getEmail()
                : (body.get("email") != null ? String.valueOf(body.get("email")).trim() : "");
        String submitterName = user != null ? user.getFullName()
                : (body.get("name") != null ? String.valueOf(body.get("name")).trim() : "");
        String category = body.get("category") != null ? String.valueOf(body.get("category")).trim() : "general";
        String subject = body.get("subject") != null ? String.valueOf(body.get("subject")).trim() : "";
        String message = body.get("message") != null ? String.valueOf(body.get("message")).trim() : "";
        // Optional linked item id, accepting either camelCase or snake_case key.
        String linkedItemId = body.get("linkedItemId") != null ? String.valueOf(body.get("linkedItemId")).trim()
                : (body.get("linked_item_id") != null ? String.valueOf(body.get("linked_item_id")).trim() : null);

        if (subject.isBlank()) throw new BadRequestException("Subject is required.");
        if (message.isBlank()) throw new BadRequestException("Message is required.");

        SupportTicket ticket = service.create(submitterEmail, submitterName, category, subject, message, linkedItemId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketNumber", ticket.getTicketNumber());
        result.put("id", ticket.getId());
        result.put("status", ticket.getStatus());
        return result;
    }

    /**
     * GET {@code /api/support/tickets/{ticketNumber}} — look up a ticket by its human-facing number.
     *
     * <p>Although under the public {@code /api/support} prefix, this is staff/admin-only: ticket
     * records carry submitter PII and internal staff notes, and ticket numbers are guessable.
     *
     * @param ticketNumber the public ticket number (path variable).
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller (must be staff/admin).
     * @return 200 OK with the {@link SupportTicket}.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/api/support/tickets/{ticketNumber}")
    public SupportTicket getByTicketNumber(
            @PathVariable String ticketNumber,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail) {
        // Ticket records carry submitter PII and internal staff notes, and ticket
        // numbers are guessable — restrict lookups to staff/admin.
        authorizationService.requireStaffOrAdmin(userEmail);
        return service.getByTicketNumber(ticketNumber);
    }

    // ─── Staff endpoints ───────────────────────────────────────────────────────

    /**
     * GET {@code /api/staff/support/tickets} — list tickets for staff, optionally filtered by status.
     *
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller (must be staff/admin).
     * @param status optional status filter; absent returns all tickets.
     * @return 200 OK with the matching {@link SupportTicket}s.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @GetMapping("/api/staff/support/tickets")
    public List<SupportTicket> listAll(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestParam(required = false) String status
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return service.listAll(status);
    }

    /**
     * PATCH {@code /api/staff/support/tickets/{id}} — staff update of a ticket's status and/or notes.
     *
     * <p>Both fields are optional (null = leave unchanged): {@code status} (trimmed) and the internal
     * {@code staffNotes}/{@code staff_notes} text.
     *
     * @param id the ticket id (path variable).
     * @param body loosely-typed partial update body.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller (must be staff/admin).
     * @return 200 OK with the updated {@link SupportTicket}.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     */
    @PatchMapping("/api/staff/support/tickets/{id}")
    public SupportTicket updateTicket(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        String status = body.get("status") != null ? String.valueOf(body.get("status")).trim() : null; // null -> unchanged
        // Internal notes, accepting either camelCase or snake_case key; null -> unchanged.
        String staffNotes = body.get("staffNotes") != null ? String.valueOf(body.get("staffNotes"))
                : (body.get("staff_notes") != null ? String.valueOf(body.get("staff_notes")) : null);
        return service.updateByStaff(id, status, staffNotes);
    }

    /**
     * POST {@code /api/staff/support/tickets/{id}/reply} — staff posts a reply on a ticket thread.
     *
     * <p>The reply is recorded as a {@link CaseMessage} attributed to the acting staff member's email
     * and role.
     *
     * @param id the ticket id (path variable).
     * @param body request body; {@code message} (required, trimmed) is the reply text.
     * @param userEmail the {@code X-Demo-User-Email} header identifying the caller (must be staff/admin).
     * @return 201 CREATED with the newly added {@link CaseMessage}.
     * @throws ForbiddenException (403) if the caller is not staff/admin.
     * @throws BadRequestException (400) if {@code message} is blank.
     */
    @PostMapping("/api/staff/support/tickets/{id}/reply")
    @ResponseStatus(HttpStatus.CREATED) // success returns HTTP 201
    public CaseMessage reply(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        AppUser staff = authorizationService.requireStaffOrAdmin(userEmail);
        String message = body.get("message") != null ? String.valueOf(body.get("message")).trim() : "";
        if (message.isBlank()) throw new BadRequestException("Message is required.");
        return service.addStaffReply(id, staff.getEmail(), staff.getRole(), message);
    }
}
