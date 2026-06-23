# Lost Then Found Frontend Backend Integration Brief

Use this file as the prompt/context for the frontend AI. It describes the current Spring Boot + MongoDB backend surface and how the frontend should integrate with it.

## Global Rules

- Base API path is the backend origin plus `/api`.
- JSON uses `snake_case` in requests and responses.
- Most admin/staff endpoints require header:

```http
X-Demo-User-Email: avery.patel@pleasantvalley.edu
```

- Public endpoints do not require the header.
- Signed-in user endpoints require `X-Demo-User-Email` for the current user.
- Dates are ISO strings.
- Never show these private fields in public UI: `storage_location`, `private_verification_clues`, Proof Vault clues, claimant private evidence, Return Pass tokens, sensitive item handoff locations.
- Prefer specific feature endpoints below. Use `/api/entities/*` only for legacy CRUD screens.

## User/Auth

### `POST /api/auth/signin`

Request:

```json
{
  "email": "jordan.kim@pleasantvalley.edu",
  "full_name": "Jordan Kim"
}
```

Response: `AppUser`

Important fields:

```json
{
  "id": "user_...",
  "full_name": "Jordan Kim",
  "email": "jordan.kim@pleasantvalley.edu",
  "role": "student",
  "phone_number": null,
  "email_notifications_enabled": true,
  "sms_opt_in": false,
  "sms_notifications_enabled": false,
  "webhook_notifications_enabled": true,
  "notification_categories": ["all"]
}
```

### `GET /api/auth/user?email={email}`

Returns the user or JSON `null`.

Frontend should keep the signed-in email and send it in `X-Demo-User-Email`.

## Found Items

### Public Item Browse

`GET /api/items`

Returns public `PublicFoundItemResponse[]`. This excludes restricted items and private fields.

Fields:

```json
{
  "id": "found_001",
  "title": "Black Hydro Flask Water Bottle",
  "description": "Public description",
  "category": "food_containers",
  "subcategory": null,
  "color": "Black",
  "brand": "Hydro Flask",
  "location_found": "Gymnasium",
  "date_found": "2026-03-11",
  "time_found": "12:15",
  "status": "approved",
  "record_type": "found",
  "condition": null,
  "priority": null,
  "event_hub_id": "hub_basketball_game",
  "campus_zone_id": "zone_gym_bleachers",
  "photo_urls": ["/items/example.jpg"],
  "tags": ["water bottle", "black"]
}
```

`GET /api/items/{id}` returns one public item.

### Admin Item Inventory

`GET /api/admin/items` requires admin header and returns full `FoundItem[]`, including admin-only fields.

### Create/Update/Delete Found Item

`POST /api/items`

`PATCH /api/items/{id}`

`DELETE /api/items/{id}`

Suggested create body:

```json
{
  "title": "Silver Graphing Calculator",
  "description": "Silver graphing calculator found near gym entrance.",
  "category": "electronics",
  "color": "Silver",
  "brand": "Texas Instruments",
  "location_found": "Gym Entrance",
  "date_found": "2026-03-14",
  "time_found": "19:30",
  "status": "approved",
  "photo_urls": ["/uploads/demo.png"],
  "tags": ["calculator", "silver"],
  "event_hub_id": "hub_basketball_game",
  "campus_zone_id": "zone_gym_entrance"
}
```

Admin-only/private fields may exist on full item records: `storage_location`, `finder_email`, `private_verification_clues`, `restricted_visibility`, `asset_tag`, `department_destination`.

## Lost Reports

Use generic entity endpoint:

`POST /api/entities/LostReport`

Required fields:

```json
{
  "title": "Lost black AirPods-style case",
  "category": "electronics",
  "description": "Black case lost during the game.",
  "color": "Black",
  "brand": "Apple",
  "location_lost": "Gym Bleachers",
  "date_lost": "2026-03-14",
  "time_lost": "20:15",
  "contact_name": "Mia Rodriguez",
  "contact_email": "mia.rodriguez@pleasantvalley.edu",
  "urgency": "high",
  "event_hub_id": "hub_basketball_game",
  "campus_zone_id": "zone_gym_bleachers",
  "photo_urls": ["/uploads/lost-case.png"],
  "extra_notes": "Public-safe notes only"
}
```

Backend behavior:

- Creates a `LostReport`.
- Refreshes matches.
- Ensures/updates a Recovery Case.
- May trigger `strong_item_match` notification if a new strong match appears.

List/update/delete:

- `GET /api/entities/LostReport`
- `PATCH /api/entities/LostReport/{id}`
- `DELETE /api/entities/LostReport/{id}`

Statuses commonly used: `open`, `matched`, `resolved`, `closed`.

## Claims

Use generic entity endpoint:

`POST /api/entities/Claim`

Claim submission request:

```json
{
  "found_item_id": "found_airpods_game",
  "found_item_title": "Black AirPods-style Case",
  "claimant_name": "Mia Rodriguez",
  "claimant_email": "mia.rodriguez@pleasantvalley.edu",
  "claimant_phone": "",
  "claim_reason": "This matches the item I lost.",
  "identifying_details": "Private details entered by claimant.",
  "proof_photo_url": "/uploads/proof.png",
  "student_id": "optional",
  "pickup_availability": "After school"
}
```

Backend behavior:

- Validates `found_item_id`, `claimant_name`, `claimant_email`, `claim_reason`, `identifying_details`.
- Creates `claim_submitted` notification for staff/admin.
- Moves related Recovery Case into claim review.
- Marks found item as claim pending when applicable.

Admin claim status updates:

- `PATCH /api/entities/Claim/{id}` with `{ "status": "need_more_info" }`
- `PATCH /api/entities/Claim/{id}` with `{ "status": "approved" }`
- `PATCH /api/entities/Claim/{id}` with `{ "status": "rejected" }`

Admin shortcut endpoints:

- `POST /api/admin/claims/{id}/approve`
- `POST /api/admin/claims/{id}/deny`

Optional body:

```json
{
  "admin_notes": "Reviewed private evidence."
}
```

Claim statuses:

- `submitted`
- `pending_review`
- `under_review`
- `need_more_info`
- `approved`
- `rejected`
- `completed`

## AI Matchmaking

### `GET /api/matches/lost-reports/{id}`

Returns current `MatchSuggestion[]`.

### `POST /api/matches/lost-reports/{id}/refresh`

Recomputes matches for a lost report.

### `POST /api/matches/found-items/{id}/refresh`

Recomputes matches impacted by one found item.

Match fields:

```json
{
  "found_item_id": "found_airpods_game",
  "found_item_title": "Black AirPods-style Case",
  "category": "electronics",
  "color": "Black",
  "brand": "Apple",
  "location_found": "Gym Bleachers",
  "date_found": "2026-03-14",
  "confidence": 94,
  "reasons": ["category match", "color match"],
  "source": "ai",
  "status": "suggested",
  "photo_urls": ["/items/example.jpg"]
}
```

Strong new matches can trigger Recovery Pulse notifications.

## Recovery Cases and Recovery Center

Admin/staff feature.

### Dashboard

`GET /api/admin/recovery-center`

Response:

```json
{
  "summary": {
    "active_cases": 4,
    "open_missions": 2,
    "claims_awaiting_review": 1,
    "pickup_ready_cases": 1
  },
  "cases": [
    {
      "recovery_case": {},
      "lost_report": {},
      "next_action": "Review ownership evidence and approve or deny the claim.",
      "updates": ["Linked Lost Report: lost_001"],
      "missions": []
    }
  ]
}
```

### Case Endpoints

- `GET /api/recovery-cases`
- `GET /api/recovery-cases/{id}`
- `GET /api/recovery-cases/lost-reports/{lost_report_id}`
- `POST /api/admin/recovery-cases` creates a lost report and case from body.
- `POST /api/admin/recovery-cases/lost-reports/{lost_report_id}` ensures case for report.
- `POST /api/recovery-cases/lost-reports/{lost_report_id}/refresh`
- `PATCH /api/recovery-cases/{id}`
- `POST /api/recovery-cases/{id}/assign`

Assign body:

```json
{
  "assigned_to": "staff.member@pleasantvalley.edu"
}
```

Recovery Case statuses:

- `open`
- `match_identified`
- `claim_in_review`
- `pickup_ready`
- `returned`
- `closed`
- `archived`
- `paused`

Status changes create Recovery Pulse events.

### Recovery Missions

- `GET /api/recovery-cases/{id}/missions`
- `POST /api/recovery-cases/{id}/missions`
- `PATCH /api/recovery-missions/{id}`

Create/update body:

```json
{
  "title": "Check Gym Bleachers",
  "recommended_action": "Staff should check this zone.",
  "status": "assigned",
  "assigned_to": "staff.member@pleasantvalley.edu",
  "priority": "high",
  "due_at": "2026-03-15T20:00:00Z",
  "notes": "Staff-only notes"
}
```

Mission statuses:

- `open`
- `assigned`
- `checked`
- `completed`
- `canceled`

New assignees trigger Recovery Pulse.

## Proof Vault and Evidence Review

Admin-only. Do not expose this in public claimant/item browse.

### `GET /api/items/{id}/proof-vault`

Returns:

```json
{
  "found_item_id": "found_airpods_game",
  "title": "Black AirPods-style Case",
  "private_verification_clues": ["private clue"],
  "restricted_visibility": false,
  "asset_tag": null,
  "asset_record_id": null,
  "department_destination": null,
  "storage_location": "Private storage"
}
```

### `GET /api/claims/{claim_id}/evidence-review`

Returns claim evidence plus sealed clues for staff comparison.

### `POST /api/claims/{claim_id}/evidence-review`

Request:

```json
{
  "verification_score": 88,
  "verification_flags": ["strong overlap", "proof photo supplied"],
  "verification_summary": "Staff-safe summary"
}
```

## Return Pass

Admin creates Return Pass after claim approval.

### `POST /api/claims/{claim_id}/return-pass`

Admin-only.

Request:

```json
{
  "pickup_window": "Next school day during office hours",
  "pickup_location": "PVHS Main Office pickup station"
}
```

Response:

```json
{
  "id": "pass_...",
  "claim_id": "claim_...",
  "found_item_id": "found_...",
  "claimant_email": "student@pleasantvalley.edu",
  "pickup_window": "Next school day during office hours",
  "pickup_location": "PVHS Main Office pickup station",
  "status": "active",
  "one_time_code": "314159",
  "expires_at": "2026-12-31T23:59:00Z",
  "redeemed_at": null,
  "redeemed_by": null
}
```

Important frontend rule: show `one_time_code` only to claimant/admin in the Return Pass UI. Never place it in notifications.

### `GET /api/return-passes/{id}`

Requires claimant email header or admin header.

### `POST /api/return-passes/verify`

Request:

```json
{
  "one_time_code": "314159"
}
```

Use at pickup station before redeeming.

### `POST /api/return-passes/{id}/redeem`

Admin-only.

Request:

```json
{
  "one_time_code": "314159"
}
```

Marks pass redeemed, claim completed, item returned/archived, Recovery Case returned, and creates Recovery Pulse event.

### `POST /api/return-passes/{id}/reminder`

Admin-only. Sends a pickup reminder through Recovery Pulse.

## Recovery Pulse Notifications

See also `docs/RECOVERY_PULSE_API_CONTRACT.md`.

### Preferences

`GET /api/recovery-pulse/preferences`

`PATCH /api/recovery-pulse/preferences`

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

- Phone must be E.164.
- SMS is skipped unless `sms_opt_in` and `sms_notifications_enabled` are both true.
- Empty categories or `["all"]` means all categories.

Categories:

- `matches`
- `recovery_cases`
- `claims`
- `return_pass`
- `missions`
- `pattern_review`

### Notification History

`GET /api/recovery-pulse/notifications`

Returns current user's in-app notification list.

Notification fields:

```json
{
  "id": "notif_...",
  "user_email": "student@pleasantvalley.edu",
  "title": "Return Pass ready",
  "message": "Your Return Pass is ready. Open Lost Then Found for secure pickup instructions.",
  "type": "return_pass_ready",
  "link": "/return-pass/pass_...",
  "related_item_id": "found_...",
  "is_read": false,
  "created_date": "2026-03-10T10:00:00Z"
}
```

### Delivery History

`GET /api/recovery-pulse/deliveries`

`GET /api/recovery-pulse/admin/deliveries?channel=sms`

Delivery fields:

```json
{
  "id": "ndel_...",
  "notification_id": "notif_...",
  "recipient_user_email": "student@pleasantvalley.edu",
  "recipient_email": "student@pleasantvalley.edu",
  "recipient_phone_masked": "demo-masked",
  "channel": "sms",
  "event_type": "return_pass_ready",
  "delivery_status": "mock_sent",
  "provider": "mock_sms",
  "provider_message_id": "mock_sms_...",
  "error_message": null,
  "safe_message_preview": "Return Pass ready - Your Return Pass is ready.",
  "is_demo": true
}
```

Delivery states:

- `queued`
- `sent`
- `failed`
- `skipped`
- `mock_sent`

Event types:

- `strong_item_match`
- `recovery_case_status_update`
- `claim_submitted`
- `claim_more_info_requested`
- `claim_approved`
- `claim_rejected`
- `return_pass_ready`
- `pickup_reminder`
- `item_returned`
- `recovery_mission_assigned`
- `pattern_review_alert`

### Admin Test Notification

`POST /api/recovery-pulse/admin/test`

Request:

```json
{
  "event_type": "pattern_review_alert",
  "simulate_webhook": true
}
```

This sends only to the signed-in admin's own configured contact info or records mock deliveries.

## Pattern Review / Loss Sentinel

Admin-only.

- `GET /api/sentinel/alerts`
- `POST /api/sentinel/recompute`
- `PATCH /api/sentinel/alerts/{id}`
- `POST /api/sentinel/alerts/{id}/acknowledge`
- `POST /api/sentinel/alerts/{id}/dismiss`
- `POST /api/sentinel/alerts/{id}/resolve`
- `GET /api/sentinel/alerts/{id}/source-reports`
- `POST /api/sentinel/alerts/{id}/mission`

Alert statuses include `open`, `acknowledged`, `dismissed`, `resolved`.

Dismiss/resolve body can include:

```json
{
  "resolution_notes": "Reviewed and resolved."
}
```

`POST /api/sentinel/recompute` returns:

```json
{
  "state": "alerts_created",
  "message": "1 Pattern Review alert(s) created or updated from real Lost Reports.",
  "alerts": [],
  "total_reports": 12,
  "recent_window_start": "2026-06-15",
  "recent_window_end": "2026-06-22",
  "baseline_window_start": "2026-05-16",
  "baseline_window_end": "2026-06-14",
  "calculated_at": "2026-06-22T12:00:00Z"
}
```

New alerts create Recovery Pulse Pattern Review notifications/webhook records.

## Custody Ledger

- `GET /api/custody/items/{found_item_id}`
- `GET /api/custody/items/{found_item_id}/verify`
- `POST /api/custody/items/{found_item_id}/move`

Move request:

```json
{
  "to_location": "PVHS Main Office pickup station",
  "notes": "Moved for pickup"
}
```

Public users get redacted custody events. Admin gets full custody records.

## Event Recovery Hubs

Public:

- `GET /api/campus-zones`
- `GET /api/event-hubs`
- `GET /api/event-hubs/{id}`
- `GET /api/event-hubs/{id}/display-feed`

Admin:

- `POST /api/admin/event-hubs`
- `PATCH /api/admin/event-hubs/{id}`
- `POST /api/admin/event-hubs/{id}/activate`
- `POST /api/admin/event-hubs/{id}/close`

Create/update body:

```json
{
  "name": "PVHS Basketball Game",
  "description": "Event recovery hub",
  "event_type": "athletics",
  "start_time": "2026-03-14T18:00:00Z",
  "end_time": "2026-03-14T21:00:00Z",
  "campus_zone_ids": ["zone_gym_entrance", "zone_gym_bleachers"],
  "public_enabled": true,
  "display_enabled": true
}
```

Display feed returns hub, zones, and public found items for signage/event screens.

## Asset Registry

`GET /api/assets/lookup?tag=PVHS-CB-1042`

Use for staff/admin intake when a found item has a school asset tag.

## Partner Relay

- `GET /api/recovery-nodes`
- `GET /api/partner-relays`

Use for partner/admin visibility screens. Relays expose redacted match reasons, not private Proof Vault details.

## Uploads

`POST /api/uploads`

Request shape is `UploadRequest`; intended for frontend-provided file metadata/base64 depending on current upload UI. Response is `UploadResponse` with the stored URL/path.

## Generic Entity CRUD

Legacy/general endpoint:

- `GET /api/entities/{entityName}`
- `POST /api/entities/{entityName}`
- `PATCH /api/entities/{entityName}/{id}`
- `DELETE /api/entities/{entityName}/{id}`

Supported entity names:

- `LostReport`
- `Claim`
- `Notification`
- `AuditLog`

Prefer feature-specific endpoints when available.

## Health

`GET /api/health`

Use for frontend backend connectivity checks.

## Recommended Frontend Screens

Student/public:

- Sign in: `POST /api/auth/signin`
- Browse found items: `GET /api/items`
- Submit lost report: `POST /api/entities/LostReport`
- Submit claim: `POST /api/entities/Claim`
- Match list for lost report: `GET /api/matches/lost-reports/{id}`
- Return Pass view: `GET /api/return-passes/{id}`
- Notifications: `GET /api/recovery-pulse/notifications`
- Notification preferences: `GET/PATCH /api/recovery-pulse/preferences`

Admin/staff:

- Dashboard: `GET /api/admin/dashboard`
- Recovery Center: `GET /api/admin/recovery-center`
- Full inventory: `GET /api/admin/items`
- Claims: `GET /api/admin/claims`
- Claim approval/denial: `POST /api/admin/claims/{id}/approve`, `POST /api/admin/claims/{id}/deny`
- Proof Vault/Evidence Review endpoints
- Return Pass create/redeem/reminder endpoints
- Pattern Review endpoints
- Recovery Pulse delivery ledger
- Event Hub management
- Custody ledger

## Backend Side Effects Frontend Should Expect

- Creating a Lost Report can create/refresh a Recovery Case and matches.
- Creating a Claim can change found item status and Recovery Case status.
- Approving a Claim verifies the found item and sends Recovery Pulse.
- Creating a Return Pass sends Recovery Pulse and marks pickup ready.
- Redeeming a Return Pass completes claim/item/case and sends Recovery Pulse.
- Strong new matches, mission assignment, and Pattern Review alerts create notifications automatically.
- Provider failures never break the user workflow; check delivery history for `failed` or `skipped`.

## Demo Seed Records

Useful seeded identities:

- Admin: `avery.patel@pleasantvalley.edu`
- Student: `jordan.kim@pleasantvalley.edu`
- Student: `riley.chen@pleasantvalley.edu`

Useful seeded IDs:

- Return Pass: `pass_calculator_active`
- Approved claim: `claim_calculator_approved`
- Strong match report: `lost_airpods_game`
- Recovery Case: `case_airpods_game`
- Event Hub: `hub_basketball_game`

Safe demo path:

1. Sign in as admin.
2. Open Recovery Center.
3. View Pattern Review alerts.
4. Open Recovery Pulse admin deliveries.
5. Send `POST /api/recovery-pulse/admin/test`.
6. Send `POST /api/return-passes/pass_calculator_active/reminder`.
7. Show notification history and delivery statuses.
