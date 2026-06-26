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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportTicketController {
    private final SupportTicketService service;
    private final DemoAuthorizationService authorizationService;

    @Autowired
    public SupportTicketController(SupportTicketService service, DemoAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    // ─── Public endpoints ──────────────────────────────────────────────────────

    @PostMapping("/api/support/tickets")
    @ResponseStatus(HttpStatus.CREATED)
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

    @GetMapping("/api/staff/support/tickets")
    public List<SupportTicket> listAll(
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail,
            @RequestParam(required = false) String status
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        return service.listAll(status);
    }

    @PatchMapping("/api/staff/support/tickets/{id}")
    public SupportTicket updateTicket(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Demo-User-Email", required = false) String userEmail
    ) {
        authorizationService.requireStaffOrAdmin(userEmail);
        String status = body.get("status") != null ? String.valueOf(body.get("status")).trim() : null;
        String staffNotes = body.get("staffNotes") != null ? String.valueOf(body.get("staffNotes"))
                : (body.get("staff_notes") != null ? String.valueOf(body.get("staff_notes")) : null);
        return service.updateByStaff(id, status, staffNotes);
    }

    @PostMapping("/api/staff/support/tickets/{id}/reply")
    @ResponseStatus(HttpStatus.CREATED)
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
