# Event Recovery API

Actual Event Hub, QR Beacon, and demo-scenario contract used by the React frontend.
All JSON uses `snake_case`; admin routes require the normal backend admin authorization.

## Public Event Hub

- `GET /api/event-hubs`
  - Returns public-safe event contexts only (`public_enabled=true`).
  - Response fields: `id`, `tenant_id`, `name`, `description`, `event_type`, `start_time`,
    `end_time`, `status`, `campus_zone_ids`, `public_enabled`, `display_enabled`,
    `created_date`, `updated_date`.
  - Does not expose `created_by`.
- `GET /api/event-hubs/{id}`
  - Same public-safe event context; 404 if the hub is not public.
- `GET /api/event-hubs/{id}/display-feed`
  - Response: `event_hub`, `zones`, `found_items`, `notice`.
  - `found_items` are `PublicFoundItemResponse` records filtered by `event_hub_id` and public
    item visibility. Private storage, verification clues, finder contact, and Proof Vault data
    are not returned.
  - `notice` explicitly says the event context is manually configured and does not claim live
    PVHS calendar, GPS, or display-system integration.
- `GET /api/campus-zones`
  - Returns zone labels/descriptions used for Event Hub links and QR Beacon copy.

## QR Beacon Frontend Contract

Beacon URLs are frontend routes:

- `#/Beacon?zone={campus_zone_id}&event={event_hub_id}`

The Beacon page shows `You are reporting from: {zone.label}` and actions:

- `Report Found Here` → `#/ReportFound?zone=...&event=...`
- `Report Lost Here` → `#/ReportLost?zone=...&event=...`
- `Browse Nearby Items` → `#/Search?q={zone.label}`

No GPS position is read or claimed. Report forms persist `event_hub_id` and `campus_zone_id`;
the human-readable location field is prefilled from the zone label but remains editable.

## Admin Event Hub Management

- `POST /api/admin/event-hubs`
  - Body: `name`, `description`, `event_type`, `start_time`, `end_time`,
    `campus_zone_ids`, optional `public_enabled`, `display_enabled`, `status`.
  - Creates an admin-owned hub.
- `PATCH /api/admin/event-hubs/{id}`
  - Partial update; `id`, `created_date`, and `created_by` are immutable.
- `POST /api/admin/event-hubs/{id}/activate`
  - Sets `status=active`, `public_enabled=true`.
- `POST /api/admin/event-hubs/{id}/close`
  - Sets `status=closed`.

## Demo Scenario Builder

Admin routes:

- `POST /api/admin/demo-scenarios/airpods_gym`
  - Creates real `LostReport`, `RecoveryCase`, `RecoveryMission`, `FoundItem`, and `Claim`
    records for an AirPods-at-gym case.
- `POST /api/admin/demo-scenarios/approved_calculator_return`
  - Creates a real calculator `LostReport`, `FoundItem`, submitted `Claim`, approves it via
    `AdminWorkflowService`, and issues an active Return Pass via `ReturnPassService`.
- `POST /api/admin/demo-scenarios/gym_electronics_pattern`
  - Creates persisted baseline/recent `LostReport` records and recomputes Loss Sentinel; alert
    counts come from persisted records only.
- `POST /api/admin/demo-scenarios/cleanup`
  - Body must include `confirmation: "DELETE DEMO DATA"`.
  - Deletes only records where `is_demo=true`: return passes, claims, found items, recovery
    missions, recovery cases, prevention alerts, and lost reports. Non-demo data is never deleted.

`DemoScenarioResponse` fields:

- `scenario`
- `lost_report_ids`
- `recovery_case_ids`
- `recovery_mission_ids`
- `found_item_ids`
- `claim_ids`
- `details`

All scenario-created domain records are flagged `is_demo=true`. Return Pass records also carry
`is_demo=true` internally for cleanup, but pass codes/tokens are not returned in demo details.
