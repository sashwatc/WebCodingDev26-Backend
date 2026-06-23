package com.FBLA.WebCodingDev26Backend.service;

import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class RecoveryPulseTemplateService {
    public RecoveryPulseMessage render(RecoveryPulseEvent event) {
        String type = value(event.eventType()).toLowerCase(Locale.ROOT);
        String caseId = shortId(event.context().get("case_id"));
        String claimId = shortId(event.context().get("claim_id"));

        return switch (type) {
            case "strong_item_match" -> message(
                    "Strong match available",
                    "A strong possible match is ready for review.",
                    "A strong possible match is ready in Lost Then Found. Open your dashboard to review it.",
                    "Lost Then Found: a strong possible match is ready for review.",
                    "Strong match alert created."
            );
            case "recovery_case_status_update" -> {
                String status = safeStatus(event.context().get("status"));
                yield message(
                        "Recovery Case updated",
                        "Recovery Case " + caseId + " moved to " + status + ".",
                        "Recovery Case " + caseId + " has a new status: " + status + ". Open Lost Then Found for details.",
                        "Lost Then Found: Recovery Case " + caseId + " moved to " + status + ".",
                        "Recovery Case " + caseId + " status changed to " + status + "."
                );
            }
            case "claim_submitted" -> message(
                    "New claim submitted",
                    "A claimant submitted a new ownership claim.",
                    "A new ownership claim " + claimId + " is ready for staff review in Lost Then Found.",
                    "Lost Then Found: new claim " + claimId + " is ready for review.",
                    "Claim " + claimId + " submitted for staff review."
            );
            case "claim_more_info_requested" -> message(
                    "More claim information requested",
                    "Staff requested more information for your claim.",
                    "Staff requested more information for claim " + claimId + ". Open Lost Then Found to respond.",
                    "Lost Then Found: staff requested more information for claim " + claimId + ".",
                    "More information requested for claim " + claimId + "."
            );
            case "claim_approved" -> message(
                    "Claim approved",
                    "Your claim was approved. Follow the pickup instructions in Lost Then Found.",
                    "Your claim " + claimId + " was approved. Open Lost Then Found for the secure pickup process.",
                    "Lost Then Found: your claim " + claimId + " was approved.",
                    "Claim " + claimId + " approved."
            );
            case "claim_rejected" -> message(
                    "Claim rejected",
                    "Your claim could not be verified from the information provided.",
                    "Claim " + claimId + " could not be verified. Open Lost Then Found for next steps.",
                    "Lost Then Found: claim " + claimId + " could not be verified.",
                    "Claim " + claimId + " rejected."
            );
            case "return_pass_ready" -> message(
                    "Return Pass ready",
                    "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions.",
                    "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions and staff verification.",
                    "Lost Then Found: your Return Pass is ready.",
                    "Return Pass ready for secure pickup."
            );
            case "pickup_reminder" -> message(
                    "Pickup reminder",
                    "Reminder: your approved item is still waiting for pickup.",
                    "Reminder: your approved item is still waiting for pickup. Open Lost Then Found for the secure pickup process.",
                    "Lost Then Found reminder: your approved pickup is still waiting.",
                    "Pickup reminder sent."
            );
            case "item_returned" -> message(
                    "Item successfully returned",
                    "Your recovery has been marked complete.",
                    "Your recovery has been marked complete. Thank you for using Lost Then Found.",
                    "Lost Then Found: your recovery is complete.",
                    "Item return completed."
            );
            case "recovery_mission_assigned" -> message(
                    "Recovery Mission assigned",
                    "A Recovery Mission was assigned to you.",
                    "A Recovery Mission was assigned to you in Lost Then Found. Open the staff dashboard for details.",
                    "Lost Then Found: a Recovery Mission was assigned to you.",
                    "Recovery Mission assigned to staff."
            );
            case "pattern_review_alert" -> message(
                    "Pattern Review alert",
                    "A loss pattern needs admin review.",
                    "A loss pattern needs admin review in Lost Then Found. Open Pattern Review for the redacted summary.",
                    "Lost Then Found: Pattern Review alert needs admin attention.",
                    "Pattern Review alert created."
            );
            default -> message(
                    "Lost Then Found update",
                    "A recovery update is ready in Lost Then Found.",
                    "A recovery update is ready in Lost Then Found. Open your dashboard for details.",
                    "Lost Then Found: a recovery update is ready.",
                    "Recovery update created."
            );
        };
    }

    private RecoveryPulseMessage message(String title, String inApp, String email, String sms, String webhookSummary) {
        return new RecoveryPulseMessage(
                title,
                inApp,
                title,
                email,
                sms,
                webhookSummary,
                preview(title, inApp)
        );
    }

    private String preview(String title, String message) {
        String preview = (value(title) + " - " + value(message)).replaceAll("\\s+", " ").trim();
        return preview.length() > 180 ? preview.substring(0, 180) : preview;
    }

    private String safeStatus(Object raw) {
        String status = value(raw).replace('_', ' ').trim();
        return status.isBlank() ? "updated" : status;
    }

    private String shortId(Object raw) {
        String id = value(raw).trim();
        if (id.isBlank()) {
            return "record";
        }
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    private String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }
}
