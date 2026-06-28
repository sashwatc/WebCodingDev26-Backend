package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.dto.CustodyVerificationResponse;
import com.FBLA.WebCodingDev26Backend.dto.MoveItemRequest;
import com.FBLA.WebCodingDev26Backend.model.CustodyEvent;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.repository.CustodyEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Maintains a tamper-evident custody ledger (a hash chain) for each found item,
 * recording the chain of custody from intake through return/archival.
 *
 * <p>How the integrity guarantee works: events for an item are kept in an ordered,
 * append-only sequence. Each event stores a SHA-256 {@code eventHash} over its
 * canonical contents plus the hash of the previous event ({@code previousEventHash}),
 * forming a blockchain-style chain. {@link #verify} can later re-walk the chain to
 * detect any reordering, gaps, broken links, or after-the-fact data tampering.
 *
 * <p>Collaborators: {@link CustodyEventRepository} (persistence) and
 * {@link ClockService} (timestamps). Owns the allowed event-type vocabulary and the
 * hashing scheme.
 */
@Service
public class CustodyLedgerService {
    /** Whitelist of valid custody event types; appending any other type is rejected. */
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "intake_created", "reviewed", "approved", "rejected", "moved", "matched", "claim_submitted",
            "claim_approved", "pickup_ready", "handoff_verified", "returned", "archived"
    );

    /** Persistence for custody events, queried/ordered by sequence number per item. */
    private final CustodyEventRepository custodyEvents;
    /** Supplies the creation timestamp baked into each event (and its hash). */
    private final ClockService clock;

    /** Injects the custody-event repository and clock. */
    public CustodyLedgerService(CustodyEventRepository custodyEvents, ClockService clock) {
        this.custodyEvents = custodyEvents;
        this.clock = clock;
    }

    /**
     * Lists an item's custody events in chain order.
     *
     * @param foundItemId the item whose ledger to read
     * @return events ordered by ascending sequence number (read-only)
     */
    public List<CustodyEvent> list(String foundItemId) {
        return custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
    }

    /**
     * Appends a new event to an item's custody chain.
     *
     * <p>Steps: (1) validate the event type against {@link #ALLOWED_EVENT_TYPES};
     * (2) load the existing chain to find the last event; (3) assign the next sequence
     * number (1 if first); (4) default actor email/role when blank; (5) link to the
     * previous event's hash; (6) compute and store this event's SHA-256 hash; (7) save.
     *
     * @param foundItemId      the item this event belongs to
     * @param eventType        one of {@link #ALLOWED_EVENT_TYPES}
     * @param actorEmail       acting user's email (defaults to a system identity if blank)
     * @param actorRole        acting user's role (defaults to {@code system} if blank)
     * @param location         optional location detail
     * @param notes            optional free-text notes
     * @param photoEvidenceUrl optional evidence photo URL
     * @return the persisted custody event (side effect: one DB insert)
     * @throws IllegalArgumentException if {@code eventType} is not allowed
     */
    public CustodyEvent appendEvent(String foundItemId, String eventType, String actorEmail, String actorRole, String location, String notes, String photoEvidenceUrl) {
        // Reject any event type outside the allowed vocabulary.
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Unsupported custody event type: " + eventType);
        }
        // Load the chain so far; the last element is the predecessor we link to.
        List<CustodyEvent> existing = custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
        CustodyEvent previous = existing.isEmpty() ? null : existing.get(existing.size() - 1);
        String now = clock.now();
        CustodyEvent event = new CustodyEvent();
        // Compact generated id.
        event.setId("custody_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        event.setFoundItemId(foundItemId);
        // Sequence starts at 1 and increments from the previous event.
        event.setSequenceNumber(previous == null ? 1 : previous.getSequenceNumber() + 1);
        event.setEventType(eventType);
        // Default the actor identity for system-generated events.
        event.setActorEmail(valueOrDefault(actorEmail, "system@pvhs.demo"));
        event.setActorRole(valueOrDefault(actorRole, "system"));
        event.setLocation(location);
        event.setNotes(notes);
        event.setPhotoEvidenceUrl(photoEvidenceUrl);
        // Chain link: genesis event has an empty previous hash, others copy the prior hash.
        event.setPreviousEventHash(previous == null ? "" : previous.getEventHash());
        event.setCreatedDate(now);
        // Seal the event by hashing its canonical contents (which include the previous-hash link).
        event.setEventHash(hash(event));
        return custodyEvents.save(event);
    }

    /**
     * Convenience wrapper that appends a {@code moved} custody event from a move request.
     *
     * @param foundItemId the item being moved
     * @param request     move details (destination, note, optional photo evidence)
     * @param admin       the staff/admin performing the move (recorded as actor)
     * @return the appended custody event
     */
    public CustodyEvent move(String foundItemId, MoveItemRequest request, AppUser admin) {
        return appendEvent(
                foundItemId,
                "moved",
                admin.getEmail(),
                admin.getRole(),
                request.destination(),
                request.note(),
                request.photoEvidenceUrl()
        );
    }

    /**
     * Re-walks an item's custody chain and reports any integrity problems.
     *
     * <p>For each event in sequence order it checks three things: the sequence number is
     * present and exactly the expected next value (detects gaps/reordering); the stored
     * {@code previousEventHash} matches the actual prior event's hash (detects broken
     * links); and recomputing the event's hash matches the stored hash (detects altered
     * data). Each violation adds a human-readable issue string.
     *
     * @param foundItemId the item whose chain to verify
     * @return a response with the item id, an overall valid flag (no issues), the event
     *         count, and the list of detected issues. Read-only; no writes.
     */
    public CustodyVerificationResponse verify(String foundItemId) {
        List<CustodyEvent> events = custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
        List<String> issues = new ArrayList<>();
        // Expected previous-hash starts empty (genesis) and the first sequence number is 1.
        String previousHash = "";
        int expectedSequence = 1;

        for (CustodyEvent event : events) {
            // 1) Sequence must be present and strictly the next expected value.
            if (event.getSequenceNumber() == null || event.getSequenceNumber() != expectedSequence) {
                issues.add("Missing or out-of-order sequence at event " + valueOrDefault(event.getId(), "unknown"));
            }
            // 2) Stored previous-hash must equal the actual prior event's hash.
            if (!valueOrDefault(event.getPreviousEventHash(), "").equals(previousHash)) {
                issues.add("Broken previous-hash link at sequence " + event.getSequenceNumber());
            }
            // 3) Recomputed hash must match the stored hash, else the data was altered.
            String recomputed = hash(event);
            if (!recomputed.equals(event.getEventHash())) {
                issues.add("Altered event data detected at sequence " + event.getSequenceNumber());
            }
            // Advance the rolling previous-hash and expected sequence for the next iteration.
            previousHash = valueOrDefault(event.getEventHash(), "");
            expectedSequence++;
        }

        // Chain is valid only when no issues were collected.
        return new CustodyVerificationResponse(foundItemId, issues.isEmpty(), events.size(), issues);
    }

    /**
     * Projects a custody event to the public-safe subset (sequence number, event type,
     * created date) — omitting actor, location, notes, hashes, and evidence URL.
     */
    public Map<String, Object> toPublicEvent(CustodyEvent event) {
        return Map.of(
                "sequence_number", event.getSequenceNumber(),
                "event_type", event.getEventType(),
                "created_date", event.getCreatedDate()
        );
    }

    /**
     * Computes the SHA-256 hex digest over the event's canonical payload. This is the
     * value stored as the event's hash and recomputed during {@link #verify}.
     *
     * @param event the event to hash
     * @return lowercase hex SHA-256 digest
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable
     */
    public String hash(CustodyEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonicalPayload(event).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash custody event", exception);
        }
    }

    /**
     * Builds the deterministic, pipe-delimited string that is hashed for an event.
     * Includes the item id, sequence, type, timestamp, actor, location, notes, evidence
     * URL, and the previous-event hash — so any change to these (or the chain link)
     * changes the digest. Field values are escaped via {@link #safe} so literal pipes
     * cannot forge the delimiter structure.
     */
    private String canonicalPayload(CustodyEvent event) {
        return String.join("|",
                safe(event.getFoundItemId()),
                String.valueOf(event.getSequenceNumber()),
                safe(event.getEventType()),
                safe(event.getCreatedDate()),
                safe(event.getActorEmail()),
                safe(event.getActorRole()),
                safe(event.getLocation()),
                safe(event.getNotes()),
                safe(event.getPhotoEvidenceUrl()),
                safe(event.getPreviousEventHash())
        );
    }

    /** Returns {@code value} unless it is null/blank, in which case the fallback is returned. */
    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Null-safe field encoder for the canonical payload: null becomes "" and literal pipes are escaped. */
    private String safe(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }
}
