package com.FBLA.WebCodingDev26Backend.service;

import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

    private final NotificationRepository notifications;
    private final ClockService clock;
    private final boolean demoMode;
    private final String fromAddress;
    private final String pickupLocation;
    private final String pickupHours;

    public EmailNotificationService(
            NotificationRepository notifications,
            ClockService clock,
            @Value("${app.email.demo-mode:true}") boolean demoMode,
            @Value("${app.email.from:lostthenfound@pvhs.demo}") String fromAddress,
            @Value("${app.pickup.location:PVHS Main Office pickup station}") String pickupLocation,
            @Value("${app.pickup.hours:School days, 8:00 AM-3:30 PM}") String pickupHours
    ) {
        this.notifications = notifications;
        this.clock = clock;
        this.demoMode = demoMode;
        this.fromAddress = fromAddress;
        this.pickupLocation = pickupLocation;
        this.pickupHours = pickupHours;
    }

    public Notification sendClaimApproved(Claim claim, FoundItem item) {
        String subject = "Your lost item claim was approved";
        String body = """
                Good news - your ownership claim has been verified.

                Item: %s
                Pickup location: %s
                Pickup hours: %s

                Bring your student ID and check in with the main office. A staff member will compare your ID to the approved claim before releasing the item.
                """.formatted(valueOrDefault(item.getTitle(), "Found item"), pickupLocation, pickupHours);

        logPreview(claim.getClaimantEmail(), subject, body);
        return saveNotification(claim.getClaimantEmail(), subject, body, "claim_approved", "/claim?claim=" + claim.getId(), item.getId());
    }

    public Notification sendClaimDenied(Claim claim, FoundItem item) {
        String subject = "Your lost item claim needs more review";
        String body = """
                Your claim was reviewed but could not be verified from the details provided.

                Item: %s
                Next step: submit a new claim with a more specific hidden detail or visit the main office for help.
                """.formatted(valueOrDefault(item.getTitle(), "Found item"));

        logPreview(claim.getClaimantEmail(), subject, body);
        return saveNotification(claim.getClaimantEmail(), subject, body, "claim_denied", "/claim?item=" + item.getId(), item.getId());
    }

    private void logPreview(String to, String subject, String body) {
        if (demoMode) {
            LOGGER.info("Demo email preview from={} to={} subject={} body={}", fromAddress, to, subject, body.replace('\n', ' '));
            return;
        }
        LOGGER.info("Email provider is not configured; safely logged preview from={} to={} subject={}", fromAddress, to, subject);
    }

    private Notification saveNotification(String email, String title, String message, String type, String link, String itemId) {
        String now = clock.now();
        Notification notification = new Notification();
        notification.setId("notif_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        notification.setUserEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRelatedItemId(itemId);
        notification.setIsRead(false);
        notification.setCreatedDate(now);
        notification.setUpdatedDate(now);
        return notifications.save(notification);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
