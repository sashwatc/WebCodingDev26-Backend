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

/**
 * Manages the support / help-desk ticketing workflow: submitters open tickets,
 * staff triage them (status + internal notes) and reply, and replies notify the
 * submitter.
 *
 * <p>Business logic owned: ticket creation with a human-readable ticket number,
 * lookup by number, listing/filtering by status, staff status+notes updates, and
 * threaded staff replies (which auto-advance an "open" ticket to "in_progress").
 * Replies are persisted as {@link CaseMessage} rows keyed under a {@code "ticket:"}
 * namespace so they reuse the same message store as claim conversations.
 *
 * <p>Collaborators: {@link SupportTicketRepository} and {@link CaseMessageRepository}
 * for persistence, {@link ClockService} for timestamps, and
 * {@link RecoveryPulseDispatcher} to send a "support_reply" notification to the
 * submitter (best-effort; failures never break the reply).
 */
@Service
public class SupportTicketService {
    /** Persistence gateway for {@link SupportTicket} records. */
    private final SupportTicketRepository repository;
    /** Persistence gateway for {@link CaseMessage} reply threads. */
    private final CaseMessageRepository caseMessages;
    /** Dispatcher that delivers submitter-facing notifications (e.g. email/SMS) on staff replies. */
    private final RecoveryPulseDispatcher recoveryPulse;
    /** Abstraction over "now" so timestamps are consistent and testable. */
    private final ClockService clock;

    /**
     * Constructs the service with its persistence and notification collaborators
     * (injected by Spring).
     *
     * @param repository    ticket persistence
     * @param caseMessages  reply-message persistence
     * @param recoveryPulse notification dispatcher for submitter alerts
     * @param clock         timestamp source
     */
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

    /**
     * Creates and persists a new support ticket in the "open" state.
     *
     * <p>Subject and message are required; other fields are normalized (trimmed,
     * with sensible defaults — empty submitter fields and "general" category). The
     * ticket receives a generated internal id and a human-readable ticket number,
     * and its created/updated timestamps are set to now.
     *
     * @param submitterEmail submitter's email (nullable → stored as "")
     * @param submitterName  submitter's name (nullable → stored as "")
     * @param category       ticket category (nullable → "general")
     * @param subject        short subject line (required, non-blank)
     * @param message        the ticket body (required, non-blank)
     * @param linkedItemId   optional id of a related lost/found item
     * @return the saved {@link SupportTicket}
     * @throws BadRequestException if subject or message is missing/blank
     */
    public SupportTicket create(
            String submitterEmail, String submitterName,
            String category, String subject, String message, String linkedItemId) {
        // Validation: subject and message are the minimum required content.
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("Subject is required.");
        }
        if (message == null || message.isBlank()) {
            throw new BadRequestException("Message is required.");
        }
        String now = clock.now();
        SupportTicket ticket = new SupportTicket();
        // Compact prefixed internal id from a random UUID (dashes stripped, 10 chars).
        ticket.setId("tkt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        // Human-facing reference like "TKT-2026-0421".
        ticket.setTicketNumber(generateTicketNumber());
        // Normalize optional contact fields to trimmed strings (never null).
        ticket.setSubmitterEmail(submitterEmail == null ? "" : submitterEmail.trim());
        ticket.setSubmitterName(submitterName == null ? "" : submitterName.trim());
        // Default the category when none supplied.
        ticket.setCategory(category == null ? "general" : category.trim());
        ticket.setSubject(subject.trim());
        ticket.setMessage(message.trim());
        ticket.setLinkedItemId(linkedItemId);
        // New tickets always start "open"; both timestamps stamped to now.
        ticket.setStatus("open");
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return repository.save(ticket);
    }

    /**
     * Looks up a ticket by its human-readable ticket number.
     *
     * @param ticketNumber the public ticket number (e.g. "TKT-2026-0421")
     * @return the matching {@link SupportTicket}
     * @throws NotFoundException if no ticket has that number
     */
    public SupportTicket getByTicketNumber(String ticketNumber) {
        return repository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
    }

    /**
     * Lists tickets, optionally filtered by status.
     *
     * @param status when non-blank, restricts results to that status; otherwise all
     *               tickets are returned newest-first
     * @return the list of matching tickets
     */
    public List<SupportTicket> listAll(String status) {
        // Filtered view when a status is provided...
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status);
        }
        // ...otherwise the full list, ordered by creation time descending.
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Applies a staff triage update to a ticket: optionally changes status and/or
     * internal staff notes, then re-stamps the updated time.
     *
     * @param id         the ticket's internal id
     * @param status     new status (ignored when null/blank)
     * @param staffNotes internal notes (applied when non-null, including empty to clear)
     * @return the saved ticket
     * @throws NotFoundException if no ticket has that id
     */
    public SupportTicket updateByStaff(String id, String status, String staffNotes) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
        // Only overwrite status when a non-blank value is supplied.
        if (status != null && !status.isBlank()) {
            ticket.setStatus(status.trim());
        }
        // A non-null notes value (even empty) replaces the stored notes.
        if (staffNotes != null) {
            ticket.setStaffNotes(staffNotes.trim());
        }
        ticket.setUpdatedAt(clock.now());
        return repository.save(ticket);
    }

    /**
     * Records a staff reply to a ticket as a {@link CaseMessage}, advances an
     * "open" ticket to "in_progress", and notifies the submitter.
     *
     * <p>Steps: validate the reply body; persist a case message namespaced under
     * {@code "ticket:<id>"} so it lives alongside claim conversations; if the ticket
     * was still "open" promote it to "in_progress"; bump the ticket's updated time;
     * and, when the submitter has an email and a dispatcher is wired, fire a
     * best-effort notification.
     *
     * @param id           the ticket's internal id
     * @param staffEmail   the replying staff member's email (stored as message sender id)
     * @param staffRole    the replying staff member's role
     * @param replyMessage the reply text (required, non-blank)
     * @return the persisted {@link CaseMessage}
     * @throws NotFoundException   if no ticket has that id
     * @throws BadRequestException if the reply message is missing/blank
     */
    public CaseMessage addStaffReply(String id, String staffEmail, String staffRole, String replyMessage) {
        SupportTicket ticket = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Support ticket not found."));
        if (replyMessage == null || replyMessage.isBlank()) {
            throw new BadRequestException("Reply message is required.");
        }

        String now = clock.now();
        CaseMessage msg = new CaseMessage();
        // Compact prefixed message id from a random UUID.
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

    /**
     * Sends a best-effort "support_reply" notification to the ticket submitter via
     * the {@link RecoveryPulseDispatcher}.
     *
     * <p>The event carries the channel ("support"), the submitter's email as the
     * recipient, a deep link to the ticket, and metadata (ticket number/id and the
     * replying staff email). Any dispatch failure is swallowed so notification
     * problems never break the reply flow.
     *
     * @param ticket     the ticket being replied to
     * @param staffEmail the replying staff member's email (included as metadata)
     */
    private void notifySupportReply(SupportTicket ticket, String staffEmail) {
        try {
            recoveryPulse.dispatch(new RecoveryPulseEvent(
                    "support_reply",                                  // event type
                    "support",                                        // channel/category
                    ticket.getSubmitterEmail(),                       // recipient
                    ticket.getId(),                                   // related entity id
                    "/support/tickets/" + ticket.getTicketNumber(),   // deep link for the recipient
                    false,                                            // not high-priority/urgent
                    java.util.Map.of(                                 // template metadata
                            "ticket_number", ticket.getTicketNumber(),
                            "ticket_id", ticket.getId(),
                            "staff_email", staffEmail == null ? "" : staffEmail
                    )
            ));
        } catch (RuntimeException ex) {
            // Non-fatal: notification failure should not break the reply
        }
    }

    /**
     * Generates a human-readable ticket number of the form
     * {@code TKT-<UTC year>-<4-digit random>} (e.g. "TKT-2026-0421").
     *
     * <p>Note: the sequence is a zero-padded random number in [0,9999], not a true
     * monotonic counter, so collisions are theoretically possible.
     *
     * @return a new ticket number string
     */
    private String generateTicketNumber() {
        // Year component from the current UTC date.
        String year = DateTimeFormatter.ofPattern("yyyy").format(ZonedDateTime.now(ZoneOffset.UTC));
        // 4-digit, zero-padded pseudo-random sequence.
        String seq = String.format("%04d", (int) (Math.random() * 10000));
        return "TKT-" + year + "-" + seq;
    }
}
