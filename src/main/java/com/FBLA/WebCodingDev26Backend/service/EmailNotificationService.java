package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private final RecoveryPulseDispatcher recoveryPulse;

    public EmailNotificationService(RecoveryPulseDispatcher recoveryPulse) {
        this.recoveryPulse = recoveryPulse;
    }

    public Notification sendClaimApproved(Claim claim, FoundItem item) {
        return recoveryPulse.claimStatusChanged(claim, "claim_approved").notification();
    }

    public Notification sendClaimDenied(Claim claim, FoundItem item) {
        return recoveryPulse.claimStatusChanged(claim, "claim_rejected").notification();
    }
}
