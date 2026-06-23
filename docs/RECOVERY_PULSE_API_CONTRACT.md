# Recovery Pulse API Contract

Recovery Pulse is the event-driven notification system for Lost Then Found. Every important recovery event first creates a persistent in-app `Notification`, then records external channel delivery attempts in `NotificationDelivery`.

External channels are optional and preference-driven:

- `in_app`: always created first.
- `email`: sent or mocked when email is enabled.
- `sms`: sent or mocked only after explicit SMS opt-in and a valid E.164 phone number.
- `webhook`: optional staff/admin system delivery for Discord, Slack, Zapier, Make, or similar. Webhooks are not SMS.

No notification template may expose private storage locations, Proof Vault clues, claimant evidence, Return Pass tokens/codes, or sensitive item details.

## Auth Convention

Current demo auth uses:

```http
X-Demo-User-Email: user@pleasantvalley.edu
```

Admin endpoints require the signed-in user to have role `admin` or match `ADMIN_EMAIL`.

## Notification Preferences

### `GET /api/recovery-pulse/preferences`

Returns the signed-in user's Recovery Pulse preferences.

Response:

```json
{
  "email": "jordan.kim@pleasantvalley.edu",
  "phone_number": null,
  "email_notifications_enabled": true,
  "sms_opt_in": false,
  "sms_notifications_enabled": false,
  "webhook_notifications_enabled": true,
  "notification_categories": ["all"]
}
```

### `PATCH /api/recovery-pulse/preferences`

Updates the signed-in user's preferences.

Request:

```json
{
  "phone_number": "+15550123456",
  "email_notifications_enabled": true,
  "sms_opt_in": true,
  "sms_notifications_enabled": true,
  "webhook_notifications_enabled": true,
  "notification_categories": ["matches", "claims", "return_pass"]
}
```

Rules:

- `phone_number` must be E.164, for example `+15550123456`.
- SMS is skipped unless both `sms_opt_in` and `sms_notifications_enabled` are `true`.
- `notification_categories` may contain event categories or event types. Empty or `["all"]` means all categories.

Supported categories:

- `matches`
- `recovery_cases`
- `claims`
- `return_pass`
- `missions`
- `pattern_review`

## Notification History

### `GET /api/recovery-pulse/notifications`

Returns persistent in-app notifications for the signed-in user, newest first.

Notification fields:

- `id`
- `user_email`
- `title`
- `message`
- `type`
- `link`
- `related_item_id`
- `is_read`
- `created_date`
- `updated_date`

### `GET /api/recovery-pulse/deliveries`

Returns delivery records for the signed-in user, newest first.

Delivery fields:

- `id`
- `notification_id`
- `recipient_user_email`
- `recipient_email`
- `recipient_phone_masked`
- `channel`
- `event_type`
- `delivery_status`
- `provider`
- `provider_message_id`
- `error_message`
- `created_date`
- `sent_date`
- `safe_message_preview`
- `is_demo`

### `GET /api/recovery-pulse/admin/deliveries`

Admin-only delivery ledger.

Optional query:

```http
GET /api/recovery-pulse/admin/deliveries?channel=sms
```

## Admin Test Send

### `POST /api/recovery-pulse/admin/test`

Admin-only. Sends a simulated notification to the signed-in admin's own account. It does not accept arbitrary recipients or phone numbers.

Request:

```json
{
  "event_type": "pattern_review_alert",
  "simulate_webhook": true
}
```

Behavior:

- In `mock` mode, delivery rows are recorded as `mock_sent` and no external provider is called.
- In `live` mode, Recovery Pulse still sends only to the signed-in admin's own configured contact info.
- SMS still requires the admin's explicit SMS opt-in and E.164 phone number.
- Webhook delivery uses the configured staff/admin webhook URL.

## Return Pass Pickup Reminder

### `POST /api/return-passes/{id}/reminder`

Admin-only. Sends a Recovery Pulse `pickup_reminder` event for an active Return Pass.

Response: `ReturnPassResponse`.

## Event Triggers

| Event type | Category | Trigger |
| --- | --- | --- |
| `strong_item_match` | `matches` | A new strong item match appears during match refresh. |
| `recovery_case_status_update` | `recovery_cases` | A Recovery Case status changes by refresh, admin update, claim transition, pickup-ready, or returned flow. |
| `claim_submitted` | `claims` | A Claim is created successfully. |
| `claim_more_info_requested` | `claims` | A Claim status changes to `need_more_info`. |
| `claim_approved` | `claims` | A Claim status changes to `approved` or admin approves through the admin workflow. |
| `claim_rejected` | `claims` | A Claim status changes to `rejected` or admin denies through the admin workflow. |
| `return_pass_ready` | `return_pass` | A Return Pass is created for an approved Claim. |
| `pickup_reminder` | `return_pass` | Admin sends a reminder for an active Return Pass. |
| `item_returned` | `return_pass` | A Return Pass is redeemed or a Claim is completed. |
| `recovery_mission_assigned` | `missions` | A Recovery Mission is created or updated with a new assignee. |
| `pattern_review_alert` | `pattern_review` | Loss Sentinel creates a new Pattern Review alert. |

## Delivery States

- `queued`: delivery row was created before a live provider call.
- `sent`: provider returned a successful send result.
- `failed`: provider call failed safely.
- `skipped`: channel was disabled, missing config, missing recipient, invalid phone, or SMS opt-in was absent.
- `mock_sent`: mock mode recorded a realistic delivery without contacting a provider.

## Environment Variables

Default deployment-safe mode:

```env
NOTIFICATIONS_MODE=mock
```

Email:

```env
EMAIL_PROVIDER=resend
RESEND_API_KEY=
RESEND_FROM=
```

SMS:

```env
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_FROM_NUMBER=
```

Webhook:

```env
NOTIFICATION_WEBHOOK_URL=
NOTIFICATION_WEBHOOK_SECRET=
```

Live mode only sends when the required provider variables are configured. Missing provider config records `skipped`; it does not break the originating workflow.

## Safe Demo Data

Seed/demo records include:

- Jordan Kim with notification preferences.
- Riley Chen Return Pass ready notification.
- Jordan Kim strong match notification.
- Mock email and SMS delivery records using masked demo contact data only.
- Admin Pattern Review webhook delivery record.

Seed startup writes records directly and does not send messages.

## Safe NLC Demo Steps

1. Start backend with `NOTIFICATIONS_MODE=mock`.
2. Sign in as the seeded admin (`avery.patel@pleasantvalley.edu`).
3. Open admin deliveries: `GET /api/recovery-pulse/admin/deliveries`.
4. Run `POST /api/recovery-pulse/admin/test` with `simulate_webhook: true`.
5. Confirm the response includes an in-app notification plus `mock_sent` external deliveries.
6. Trigger `POST /api/return-passes/pass_calculator_active/reminder` to show a real workflow event creating Recovery Pulse records.
7. Show that SMS is skipped unless the signed-in user has explicit opt-in and a valid E.164 phone number.

## Implementation Files

Core:

- `RecoveryPulseDispatcher`
- `RecoveryPulseTemplateService`
- `NotificationDelivery`
- `NotificationDeliveryRepository`
- provider interfaces and adapters for Resend, Twilio-compatible SMS, and configured webhooks

Workflow hooks:

- `MatchmakingService`
- `GenericEntityService`
- `RecoveryCaseService`
- `ReturnPassService`
- `LossSentinelService`
- `EmailNotificationService`

API:

- `RecoveryPulseController`
- `ReturnPassController`
- Recovery Pulse DTOs

Tests:

- `RecoveryPulseDispatcherTest`
- updated application and seed tests
