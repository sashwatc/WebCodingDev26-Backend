package com.FBLA.WebCodingDev26Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A help/support ticket submitted by a user and worked by staff.
 *
 * <p>Persisted as a MongoDB document in the "support_tickets" collection (mapped via
 * {@code @Document}). Holds the submitter's request, its triage category/status and
 * internal staff notes.</p>
 *
 * <p>Related models: may reference a specific item via {@code linkedItemId}.</p>
 */
@Document(collection = "support_tickets")
public class SupportTicket {
    /** MongoDB document primary key (auto-generated string id). */
    @Id
    private String id;
    /** Human-friendly ticket reference; uniquely indexed so each ticket number is distinct. */
    @Indexed(unique = true)
    private String ticketNumber;
    /** Email of the user who submitted the ticket. */
    private String submitterEmail;
    /** Display name of the submitter. */
    private String submitterName;
    /** Category/topic used to triage the ticket (e.g. account, claim, technical). */
    private String category;
    /** Short subject line of the ticket. */
    private String subject;
    /** Full message/body describing the issue. */
    private String message;
    /** Optional id of an item the ticket relates to; null if not item-specific. */
    private String linkedItemId;
    /** Lifecycle status of the ticket, e.g. "open", "in_progress", "resolved", "closed". */
    private String status;
    /** Internal notes added by staff; not shown to the submitter. */
    private String staffNotes;
    /** Timestamp (ISO string) when the ticket was created. */
    private String createdAt;
    /** Timestamp (ISO string) when the ticket was last updated. */
    private String updatedAt;

    // --- Trivial getters/setters: plain field accessors with no extra logic. ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public String getSubmitterEmail() { return submitterEmail; }
    public void setSubmitterEmail(String submitterEmail) { this.submitterEmail = submitterEmail; }
    public String getSubmitterName() { return submitterName; }
    public void setSubmitterName(String submitterName) { this.submitterName = submitterName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getLinkedItemId() { return linkedItemId; }
    public void setLinkedItemId(String linkedItemId) { this.linkedItemId = linkedItemId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStaffNotes() { return staffNotes; }
    public void setStaffNotes(String staffNotes) { this.staffNotes = staffNotes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
