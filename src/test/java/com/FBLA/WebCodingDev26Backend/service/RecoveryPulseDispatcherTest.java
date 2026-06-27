package com.FBLA.WebCodingDev26Backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.FBLA.WebCodingDev26Backend.dto.ReturnPassRequest;
import com.FBLA.WebCodingDev26Backend.model.AppUser;
import com.FBLA.WebCodingDev26Backend.model.Claim;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.model.ItemStatus;
import com.FBLA.WebCodingDev26Backend.model.Notification;
import com.FBLA.WebCodingDev26Backend.model.NotificationDelivery;
import com.FBLA.WebCodingDev26Backend.model.ReturnPass;
import com.FBLA.WebCodingDev26Backend.repository.AppUserRepository;
import com.FBLA.WebCodingDev26Backend.repository.ClaimRepository;
import com.FBLA.WebCodingDev26Backend.repository.FoundItemRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationDeliveryRepository;
import com.FBLA.WebCodingDev26Backend.repository.NotificationRepository;
import com.FBLA.WebCodingDev26Backend.repository.ReturnPassRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecoveryPulseDispatcherTest {
    private static final String NOW = "2026-06-22T12:00:00Z";

    @Mock
    private NotificationRepository notifications;
    @Mock
    private NotificationDeliveryRepository deliveries;
    @Mock
    private AppUserRepository users;
    @Mock
    private ClockService clock;
    @Mock
    private EmailNotificationProvider emailProvider;
    @Mock
    private SmsNotificationProvider smsProvider;
    @Mock
    private WebhookNotificationProvider webhookProvider;

    @ParameterizedTest
    @MethodSource("supportedEvents")
    void everySupportedEventCreatesPersistentInAppNotification(String eventType, String category, boolean webhookEnabled) {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        dispatcher.dispatch(new RecoveryPulseEvent(
                eventType,
                category,
                "student@pleasantvalley.edu",
                "found_001",
                "/UserDashboard",
                webhookEnabled,
                Map.of("claim_id", "claim_001", "case_id", "case_001", "status", "pickup_ready")
        ));

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(eventType);
        assertThat(notification.getValue().getIsRead()).isFalse();

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "in_app".equals(record.getChannel()) && "sent".equals(record.getDeliveryStatus()));
    }

    @Test
    void smsIsSkippedWithoutExplicitOptIn() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(false);
        user.setSmsNotificationsEnabled(true);
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("return_pass_ready", "return_pass", false));

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "sms".equals(record.getChannel())
                        && "skipped".equals(record.getDeliveryStatus())
                        && record.getErrorMessage().contains("opt-in"));
    }

    @Test
    void mockModeNeverCallsLiveProviders() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(true);
        user.setSmsNotificationsEnabled(true);
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("pattern_review_alert", "pattern_review", true));

        verify(emailProvider, never()).send(any());
        verify(smsProvider, never()).send(any());
        verify(webhookProvider, never()).post(any());
        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "email".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()))
                .anyMatch(record -> "sms".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()))
                .anyMatch(record -> "webhook".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()));
    }

    @Test
    void liveProviderFailureRecordsFailureAndDoesNotBreakReturnPassWorkflow() {
        RecoveryPulseDispatcher dispatcher = dispatcher("live");
        AppUser user = user("claimant@pleasantvalley.edu");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(emailProvider.send(any())).thenThrow(new RuntimeException("provider unavailable"));

        ReturnPassRepository returnPasses = org.mockito.Mockito.mock(ReturnPassRepository.class);
        ClaimRepository claims = org.mockito.Mockito.mock(ClaimRepository.class);
        FoundItemRepository foundItems = org.mockito.Mockito.mock(FoundItemRepository.class);
        CustodyLedgerService custodyLedger = org.mockito.Mockito.mock(CustodyLedgerService.class);
        RecoveryCaseService recoveryCases = org.mockito.Mockito.mock(RecoveryCaseService.class);

        Claim claim = new Claim();
        claim.setId("claim_001");
        claim.setFoundItemId("found_001");
        claim.setClaimantEmail(user.getEmail());
        claim.setStatus("approved");
        FoundItem item = new FoundItem();
        item.setId("found_001");
        item.setStatus(ItemStatus.VERIFIED);

        when(claims.findById("claim_001")).thenReturn(Optional.of(claim));
        when(foundItems.findById("found_001")).thenReturn(Optional.of(item));
        when(returnPasses.findByClaimId("claim_001")).thenReturn(List.of());
        when(returnPasses.save(any(ReturnPass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnPassService service = new ReturnPassService(
                returnPasses,
                claims,
                foundItems,
                notifications,
                custodyLedger,
                recoveryCases,
                clock,
                dispatcher,
                null
        );

        assertThatCode(() -> service.create("claim_001", new ReturnPassRequest("", ""), admin()))
                .doesNotThrowAnyException();

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "in_app".equals(record.getChannel()) && "sent".equals(record.getDeliveryStatus()))
                .anyMatch(record -> "email".equals(record.getChannel()) && "failed".equals(record.getDeliveryStatus()));
    }

    @Test
    void safeTemplatesDoNotLeakPrivateData() {
        RecoveryPulseTemplateService templates = new RecoveryPulseTemplateService();

        RecoveryPulseMessage message = templates.render(new RecoveryPulseEvent(
                "return_pass_ready",
                "return_pass",
                "student@pleasantvalley.edu",
                "found_001",
                "/return-pass/pass_001",
                false,
                Map.of(
                        "storage_location", "Main Office shelf B2",
                        "private_clue", "silver initials on the hinge",
                        "claimant_evidence", "my student id is 12345",
                        "pass_token", "secret-pass-token"
                )
        ));

        String outbound = String.join(" ", message.inAppMessage(), message.emailBody(), message.smsBody(), message.webhookSummary(), message.safePreview());
        assertThat(outbound)
                .doesNotContain("Main Office shelf B2")
                .doesNotContain("silver initials")
                .doesNotContain("12345")
                .doesNotContain("secret-pass-token");
    }

    @Test
    void deliveryRecordsPersistWithSafePreviewAndMaskedSmsContact() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(true);
        user.setSmsNotificationsEnabled(true);
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("pickup_reminder", "return_pass", false));

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "sms".equals(record.getChannel())
                        && "mock_sent".equals(record.getDeliveryStatus())
                        && record.getRecipientPhoneMasked() != null
                        && !record.getRecipientPhoneMasked().contains("50123456")
                        && record.getSafeMessagePreview().contains("Pickup reminder"));
    }

    static Stream<Arguments> supportedEvents() {
        return Stream.of(
                Arguments.of("strong_item_match", "matches", false),
                Arguments.of("recovery_case_status_update", "recovery_cases", true),
                Arguments.of("claim_submitted", "claims", true),
                Arguments.of("claim_more_info_requested", "claims", false),
                Arguments.of("claim_approved", "claims", false),
                Arguments.of("claim_rejected", "claims", false),
                Arguments.of("return_pass_ready", "return_pass", false),
                Arguments.of("pickup_reminder", "return_pass", false),
                Arguments.of("item_returned", "return_pass", false),
                Arguments.of("recovery_mission_assigned", "missions", true),
                Arguments.of("pattern_review_alert", "pattern_review", true)
        );
    }

    private RecoveryPulseDispatcher dispatcher(String mode) {
        when(clock.now()).thenReturn(NOW);
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveries.save(any(NotificationDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new RecoveryPulseDispatcher(
                notifications,
                deliveries,
                users,
                clock,
                new RecoveryPulseTemplateService(),
                emailProvider,
                smsProvider,
                webhookProvider,
                mode,
                "admin@pleasantvalley.edu"
        );
    }

    private RecoveryPulseEvent event(String eventType, String category, boolean webhookEnabled) {
        return new RecoveryPulseEvent(
                eventType,
                category,
                "student@pleasantvalley.edu",
                "found_001",
                "/UserDashboard",
                webhookEnabled,
                Map.of("claim_id", "claim_001", "case_id", "case_001", "status", "pickup_ready")
        );
    }

    private AppUser user(String email) {
        AppUser user = new AppUser();
        user.setId("user_001");
        user.setEmail(email);
        user.setEmailNotificationsEnabled(true);
        user.setSmsOptIn(false);
        user.setSmsNotificationsEnabled(false);
        user.setWebhookNotificationsEnabled(true);
        user.setNotificationCategories(List.of("all"));
        return user;
    }

    private com.FBLA.WebCodingDev26Backend.model.AppUser admin() {
        AppUser admin = new AppUser();
        admin.setEmail("admin@pleasantvalley.edu");
        admin.setRole("admin");
        return admin;
    }
}
