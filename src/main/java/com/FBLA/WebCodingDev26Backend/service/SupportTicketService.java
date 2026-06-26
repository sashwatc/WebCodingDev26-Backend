package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.exception.BadRequestException;
import com.FBLA.WebCodingDev26Backend.exception.NotFoundException;
import com.FBLA.WebCodingDev26Backend.model.CaseMessage;
import com.FBLA.WebCodingDev26Backend.model.SupportTicket;
import com.FBLA.WebCodingDev26Backend.repository.CaseMessageRepository;
import com.FBLA.WebCodingDev26Backend.repository.SupportTicketRepository;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SupportTicketService {
    private final SupportTicketRepository repository;
    private final CaseMessageRepository caseMessages;
    private final RecoveryPulseDispatcher recoveryPulse;
    private final ClockService clock;

    public SupportTicketService(
            SupportTicketRepository repository,
            CaseMessageRepository caseMessages,
            RecoveryPulseDispatcher recoveryPulse,
            ClockService clock) {
        this.repository = repository;
        this.caseMessages = caseMessages;
        this.recoveryPulse = recoveryPulse;
        this.clock = clock;
    }

    public SupportTicket create(
            String submitterEmail, String submitterName,
            String category, String subject, String message, String linkedItemId) {
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("Subject is required.");
        }
        if (message == null || message.isBlank()) {
            throw new BadRequestException("Message is required.");
        }
        String now = clock.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setId("tkt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setSubmitterEmail(submitterEmail == null ? "" : submitterEmail.trim());
        ticket.setSubmitterName(submitterName == null ? "" : submitterName.trim());
        ticket.setCategory(category == null ? "general" : category.trim());
        ticket.setSubject(subject.trim());
        ticket.setMessage(message.trim());
        ticket.setLinkedItemId(linkedItemId);
        ticket.setStatus("open");
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return repository.save(ticket);
    }

    public SupportTicket getByTicketNumber(String ticketNumber) {
        return repository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
    }

    public List<SupportTicket> listAll(String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status);
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public SupportTicket updateByStaff(String id, String status, String staffNotes) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
        if (status != null && !status.isBlank()) {
            ticket.setStatus(status.trim());
        }
        if (staffNotes != null) {
            ticket.setStaffNotes(staffNotes.trim());
        }
        ticket.setUpdatedAt(clock.now());
        return repository.save(ticket);
    }

    public CaseMessage addStaffReply(String id, String staffEmail, String staffRole, String replyMessage) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
        if (replyMessage == null || replyMessage.isBlank()) {
            throw new BadRequestException("Reply message is required.");
        }

        String now = clock.now();
        CaseMessage msg = new CaseMessage();
        msg.setId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        // Use the ticket ID as claimId so messages are linked
        msg.setClaimId("ticket:" + ticket.getId());
        msg.setSenderId(staffEmail);
        msg.setSenderRole(staffRole);
        msg.setMessage(replyMessage.trim());
        msg.setCreatedAt(now);
        CaseMessage saved = caseMessages.save(msg);

        // Update ticket status to in_progress if still open
        if ("open".equalsIgnoreCase(ticket.getStatus())) {
            ticket.setStatus("in_progress");
        }
        ticket.setUpdatedAt(now);
        repository.save(ticket);

        // Notify submitter if they have an email
        if (recoveryPulse != null && !ticket.getSubmitterEmail().isBlank()) {
            notifySupportReply(ticket, staffEmail);
        }
        return saved;
    }

    private void notifySupportReply(SupportTicket ticket, String staffEmail) {
        try {
            recoveryPulse.dispatch(new RecoveryPulseEvent(
                    "support_reply",
                    "support",
                    ticket.getSubmitterEmail(),
                    ticket.getId(),
                    "/support/tickets/" + ticket.getTicketNumber(),
                    false,
                    java.util.Map.of(
                            "ticket_number", ticket.getTicketNumber(),
                            "ticket_id", ticket.getId(),
                            "staff_email", staffEmail == null ? "" : staffEmail
                    )
            ));
        } catch (RuntimeException ex) {
            // Non-fatal: notification failure should not break the reply
        }
    }

    private String generateTicketNumber() {
        String year = DateTimeFormatter.ofPattern("yyyy").format(ZonedDateTime.now(ZoneOffset.UTC));
        String seq = String.format("%04d", (int) (Math.random() * 10000));
        return "TKT-" + year + "-" + seq;
    }
}
