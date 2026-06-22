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

@Service
public class CustodyLedgerService {
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "intake_created", "reviewed", "approved", "rejected", "moved", "matched", "claim_submitted",
            "claim_approved", "pickup_ready", "handoff_verified", "returned", "archived"
    );

    private final CustodyEventRepository custodyEvents;
    private final ClockService clock;

    public CustodyLedgerService(CustodyEventRepository custodyEvents, ClockService clock) {
        this.custodyEvents = custodyEvents;
        this.clock = clock;
    }

    public List<CustodyEvent> list(String foundItemId) {
        return custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
    }

    public CustodyEvent appendEvent(String foundItemId, String eventType, String actorEmail, String actorRole, String location, String notes, String photoEvidenceUrl) {
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Unsupported custody event type: " + eventType);
        }
        List<CustodyEvent> existing = custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
        CustodyEvent previous = existing.isEmpty() ? null : existing.get(existing.size() - 1);
        String now = clock.now();
        CustodyEvent event = new CustodyEvent();
        event.setId("custody_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        event.setFoundItemId(foundItemId);
        event.setSequenceNumber(previous == null ? 1 : previous.getSequenceNumber() + 1);
        event.setEventType(eventType);
        event.setActorEmail(valueOrDefault(actorEmail, "system@pvhs.demo"));
        event.setActorRole(valueOrDefault(actorRole, "system"));
        event.setLocation(location);
        event.setNotes(notes);
        event.setPhotoEvidenceUrl(photoEvidenceUrl);
        event.setPreviousEventHash(previous == null ? "" : previous.getEventHash());
        event.setCreatedDate(now);
        event.setEventHash(hash(event));
        return custodyEvents.save(event);
    }

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

    public CustodyVerificationResponse verify(String foundItemId) {
        List<CustodyEvent> events = custodyEvents.findByFoundItemIdOrderBySequenceNumberAsc(foundItemId);
        List<String> issues = new ArrayList<>();
        String previousHash = "";
        int expectedSequence = 1;

        for (CustodyEvent event : events) {
            if (event.getSequenceNumber() == null || event.getSequenceNumber() != expectedSequence) {
                issues.add("Missing or out-of-order sequence at event " + valueOrDefault(event.getId(), "unknown"));
            }
            if (!valueOrDefault(event.getPreviousEventHash(), "").equals(previousHash)) {
                issues.add("Broken previous-hash link at sequence " + event.getSequenceNumber());
            }
            String recomputed = hash(event);
            if (!recomputed.equals(event.getEventHash())) {
                issues.add("Altered event data detected at sequence " + event.getSequenceNumber());
            }
            previousHash = valueOrDefault(event.getEventHash(), "");
            expectedSequence++;
        }

        return new CustodyVerificationResponse(foundItemId, issues.isEmpty(), events.size(), issues);
    }

    public Map<String, Object> toPublicEvent(CustodyEvent event) {
        return Map.of(
                "sequence_number", event.getSequenceNumber(),
                "event_type", event.getEventType(),
                "created_date", event.getCreatedDate()
        );
    }

    public String hash(CustodyEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonicalPayload(event).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash custody event", exception);
        }
    }

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

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }
}
