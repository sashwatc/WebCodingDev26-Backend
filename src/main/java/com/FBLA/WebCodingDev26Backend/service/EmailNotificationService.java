package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import org.springframework.stereotype.Service;

/**
 * Thin convenience facade for sending claim-decision notifications to a claimant.
 *
 * <p>Owns no notification logic itself: it simply translates "claim approved" /
 * "claim denied" outcomes into the corresponding event keys understood by the
 * {@link RecoveryPulseDispatcher}, which is responsible for composing, persisting,
 * and delivering the notification (in-app record plus any email side effects).
 *
 * <p>Collaborators: {@link RecoveryPulseDispatcher} (the real notification engine).
 */
@Service
public class EmailNotificationService {
    /** Notification engine that builds, stores, and dispatches claim-status messages. */
    private final RecoveryPulseDispatcher recoveryPulse;

    /**
     * @param recoveryPulse dispatcher that performs the actual notification work
     */
    public EmailNotificationService(RecoveryPulseDispatcher recoveryPulse) {
        this.recoveryPulse = recoveryPulse;
    }

    /**
     * Notifies the claimant that their claim was approved.
     *
     * @param claim the claim that was approved
     * @param item  the related found item (contextual; delivery is keyed off the claim)
     * @return the persisted in-app {@link Notification} produced by the dispatcher
     */
    public Notification sendClaimApproved(Claim claim, FoundItem item) {
        // Emit the "claim_approved" pulse event and surface its in-app notification.
        return recoveryPulse.claimStatusChanged(claim, "claim_approved").notification();
    }

    /**
     * Notifies the claimant that their claim was denied/rejected.
     *
     * @param claim the claim that was denied
     * @param item  the related found item (contextual; delivery is keyed off the claim)
     * @return the persisted in-app {@link Notification} produced by the dispatcher
     */
    public Notification sendClaimDenied(Claim claim, FoundItem item) {
        // Emit the "claim_rejected" pulse event and surface its in-app notification.
        return recoveryPulse.claimStatusChanged(claim, "claim_rejected").notification();
    }
}
