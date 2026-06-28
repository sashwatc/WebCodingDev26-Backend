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

/**
 * Unit tests for {@link RecoveryPulseDispatcher}, the multi-channel notification fan-out engine.
 *
 * <p>Repositories, the clock, and the email/SMS/webhook providers are mocked. The dispatcher is
 * built in "mock" or "live" mode via {@link #dispatcher(String)}. Tests verify that every supported
 * event always persists an in-app notification and delivery record; that SMS is gated on explicit
 * opt-in; that "mock" mode records mock_sent deliveries without ever calling live providers; that a
 * live provider failure is recorded as "failed" without breaking the return-pass workflow; that
 * templated messages never leak private data; and that delivery records carry a safe preview and a
 * masked phone number.</p>
 */
@ExtendWith(MockitoExtension.class)
class RecoveryPulseDispatcherTest {
    // Fixed "current time" returned by the mocked clock for deterministic timestamps.
    private static final String NOW = "2026-06-22T12:00:00Z";

    // Mocked store for the persistent in-app notification.
    @Mock
    private NotificationRepository notifications;
    // Mocked store for per-channel delivery records (the main assertion target).
    @Mock
    private NotificationDeliveryRepository deliveries;
    // Mocked user repository used to resolve the recipient's channel preferences.
    @Mock
    private AppUserRepository users;
    // Mocked clock providing a fixed timestamp.
    @Mock
    private ClockService clock;
    // Mocked live email provider; verified to be (not) called depending on mode.
    @Mock
    private EmailNotificationProvider emailProvider;
    // Mocked live SMS provider.
    @Mock
    private SmsNotificationProvider smsProvider;
    // Mocked live webhook provider.
    @Mock
    private WebhookNotificationProvider webhookProvider;

    /**
     * Scenario: each supported event type (from {@link #supportedEvents()}) is dispatched.
     * Arrange: mock dispatcher; recipient has no stored preferences (findByEmail empty), so
     * defaults apply.
     * Act: dispatch a fully-populated event of the parameterized type/category.
     * Assert: a Notification is saved whose type equals the event type and which starts unread; and
     * at least one NotificationDelivery is saved including an "in_app" channel record with status
     * "sent". Passing proves every event reliably produces a persistent, unread in-app notification.
     */
    @ParameterizedTest
    @MethodSource("supportedEvents")
    void everySupportedEventCreatesPersistentInAppNotification(String eventType, String category, boolean webhookEnabled) {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        when(users.findByEmail(anyString())).thenReturn(Optional.empty()); // no stored prefs -> defaults

        dispatcher.dispatch(new RecoveryPulseEvent(
                eventType,
                category,
                "student@pleasantvalley.edu",
                "found_001",
                "/UserDashboard",
                webhookEnabled,
                Map.of("claim_id", "claim_001", "case_id", "case_001", "status", "pickup_ready")
        ));

        // Capture the persisted notification and assert type/unread state.
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(eventType); // notification typed by event
        assertThat(notification.getValue().getIsRead()).isFalse(); // starts unread

        // At least one delivery record exists, including the always-on in-app channel marked sent.
        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "in_app".equals(record.getChannel()) && "sent".equals(record.getDeliveryStatus()));
    }

    /**
     * Scenario: a user has SMS notifications enabled and a phone number, but has NOT opted in to SMS.
     * Arrange: user with smsOptIn=false, smsNotificationsEnabled=true, a phone number; dispatch a
     * return_pass_ready event.
     * Act: dispatch the event.
     * Assert: an SMS delivery record is saved with status "skipped" and an error message mentioning
     * "opt-in". Passing proves SMS requires explicit opt-in regardless of the enabled flag (consent
     * gating).
     */
    @Test
    void smsIsSkippedWithoutExplicitOptIn() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(false); // critical: not opted in
        user.setSmsNotificationsEnabled(true); // enabled flag alone must not be enough
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("return_pass_ready", "return_pass", false));

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "sms".equals(record.getChannel())
                        && "skipped".equals(record.getDeliveryStatus()) // SMS deliberately not sent
                        && record.getErrorMessage().contains("opt-in")); // reason records the missing consent
    }

    /**
     * Scenario: the dispatcher runs in "mock" mode with a fully opted-in, all-channels-enabled user.
     * Arrange: user with SMS opt-in/enabled + phone; webhook enabled on the event.
     * Act: dispatch a pattern_review_alert event.
     * Assert: none of the live email/SMS/webhook providers are ever invoked, yet delivery records
     * for email, sms, and webhook are all saved with status "mock_sent". Passing proves mock mode
     * simulates every channel for observability without performing real external sends.
     */
    @Test
    void mockModeNeverCallsLiveProviders() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(true); // fully consented so SMS would be attempted if mode were live
        user.setSmsNotificationsEnabled(true);
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("pattern_review_alert", "pattern_review", true)); // webhook enabled too

        verify(emailProvider, never()).send(any()); // no real email
        verify(smsProvider, never()).send(any()); // no real SMS
        verify(webhookProvider, never()).post(any()); // no real webhook POST
        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "email".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()))
                .anyMatch(record -> "sms".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()))
                .anyMatch(record -> "webhook".equals(record.getChannel()) && "mock_sent".equals(record.getDeliveryStatus()));
    }

    /**
     * Scenario: in "live" mode the email provider throws while issuing a return pass.
     * Arrange: build a live dispatcher whose emailProvider.send throws; assemble a real
     * {@link ReturnPassService} wired with mocked repositories/services and an approved claim on a
     * verified item so creating a pass triggers notifications.
     * Act: call ReturnPassService.create.
     * Assert: create does NOT throw (notification failures are isolated from the core workflow), and
     * delivery records show an "in_app" "sent" record alongside an "email" "failed" record. Passing
     * proves a downstream channel outage is logged but never breaks the return-pass business flow.
     */
    @Test
    void liveProviderFailureRecordsFailureAndDoesNotBreakReturnPassWorkflow() {
        RecoveryPulseDispatcher dispatcher = dispatcher("live"); // real-provider mode
        AppUser user = user("claimant@pleasantvalley.edu");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(emailProvider.send(any())).thenThrow(new RuntimeException("provider unavailable")); // email channel is down

        // Collaborators for a real ReturnPassService that will fire the notification on pass creation.
        ReturnPassRepository returnPasses = org.mockito.Mockito.mock(ReturnPassRepository.class);
        ClaimRepository claims = org.mockito.Mockito.mock(ClaimRepository.class);
        FoundItemRepository foundItems = org.mockito.Mockito.mock(FoundItemRepository.class);
        CustodyLedgerService custodyLedger = org.mockito.Mockito.mock(CustodyLedgerService.class);
        RecoveryCaseService recoveryCases = org.mockito.Mockito.mock(RecoveryCaseService.class);

        // Approved claim on a verified item: the precondition for issuing a return pass.
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
        when(returnPasses.findByClaimId("claim_001")).thenReturn(List.of()); // no existing pass
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
                .doesNotThrowAnyException(); // email outage must not surface as a workflow error

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "in_app".equals(record.getChannel()) && "sent".equals(record.getDeliveryStatus())) // in-app still delivered
                .anyMatch(record -> "email".equals(record.getChannel()) && "failed".equals(record.getDeliveryStatus())); // failure recorded, not thrown
    }

    /**
     * Scenario: render a return_pass_ready message that carries sensitive metadata (storage
     * location, private clue, claimant evidence, pass token).
     * Arrange: build a real {@link RecoveryPulseTemplateService} and render an event whose data map
     * includes those private values.
     * Act: render the message and concatenate every outbound surface (in-app, email, SMS, webhook,
     * safe preview).
     * Assert: none of the private strings appear anywhere in the combined output. Passing proves the
     * templates never expose private custody/verification data on any channel.
     */
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

        // Concatenate every channel surface so a leak on ANY of them fails the assertion.
        String outbound = String.join(" ", message.inAppMessage(), message.emailBody(), message.smsBody(), message.webhookSummary(), message.safePreview());
        assertThat(outbound)
                .doesNotContain("Main Office shelf B2") // storage location hidden
                .doesNotContain("silver initials") // private verification clue hidden
                .doesNotContain("12345") // claimant evidence hidden
                .doesNotContain("secret-pass-token"); // raw pass token hidden
    }

    /**
     * Scenario: a successful mock SMS delivery for an opted-in user.
     * Arrange: user opted in to SMS with a phone number; dispatch a pickup_reminder event in mock
     * mode.
     * Act: dispatch the event.
     * Assert: an "sms" delivery record is saved with status "mock_sent", a non-null masked phone
     * number that does NOT contain the raw digits "50123456", and a safe message preview containing
     * "Pickup reminder". Passing proves delivery records persist a privacy-masked contact and a safe
     * preview rather than raw PII/content.
     */
    @Test
    void deliveryRecordsPersistWithSafePreviewAndMaskedSmsContact() {
        RecoveryPulseDispatcher dispatcher = dispatcher("mock");
        AppUser user = user("student@pleasantvalley.edu");
        user.setSmsOptIn(true); // consented so SMS channel is exercised
        user.setSmsNotificationsEnabled(true);
        user.setPhoneNumber("+15550123456");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        dispatcher.dispatch(event("pickup_reminder", "return_pass", false));

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, atLeast(1)).save(delivery.capture());
        assertThat(delivery.getAllValues())
                .anyMatch(record -> "sms".equals(record.getChannel())
                        && "mock_sent".equals(record.getDeliveryStatus())
                        && record.getRecipientPhoneMasked() != null // a masked contact is stored
                        && !record.getRecipientPhoneMasked().contains("50123456") // raw digits are not present
                        && record.getSafeMessagePreview().contains("Pickup reminder")); // safe, non-sensitive preview
    }

    // MethodSource: the full set of supported (eventType, category, webhookEnabled) combinations
    // exercised by everySupportedEventCreatesPersistentInAppNotification.
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

    // Helper: build the dispatcher in the given mode ("mock"/"live"), stubbing the clock to NOW and
    // wiring repository saves to echo their argument so captured records reflect what was passed in.
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

    // Helper: build a standard RecoveryPulseEvent for a student recipient with a common data payload.
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

    // Helper: build a recipient user with email enabled, SMS off by default, and webhook enabled,
    // subscribed to all notification categories.
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

    // Helper: build the admin actor used as the staff performing the return-pass action.
    private com.FBLA.WebCodingDev26Backend.model.AppUser admin() {
        AppUser admin = new AppUser();
        admin.setEmail("admin@pleasantvalley.edu");
        admin.setRole("admin");
        return admin;
    }
}
