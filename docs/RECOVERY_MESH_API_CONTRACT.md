# Recovery Mesh API Contract

This contract is authoritative for frontend integration. Do not invent API routes outside this file.

All responses use snake_case JSON. Admin routes require `X-Demo-User-Email` for a persisted user with `role: "admin"`.

## Existing Routes Kept

- `GET /api/items` returns public-safe, approved, non-restricted found items.
- `POST /api/items` creates a found item.
- `PATCH /api/items/{id}` updates a found item.
- `DELETE /api/items/{id}` archives protected found items and deletes unreferenced found items.
- `GET /api/admin/items` returns full found-item moderation records for admins.
- `GET|POST|PATCH|DELETE /api/entities/{LostReport|Claim|Notification|AuditLog}` remain available.
- `GET /api/matches/lost-reports/{id}` and refresh routes remain advisory only.

## Recovery Cases

- `GET /api/admin/recovery-center` admin only. Returns server-calculated summary counts and case rows with linked Lost Reports, next actions, missions, and updates.
- `GET /api/recovery-cases` admin only.
- `GET /api/recovery-cases/{id}` admin only.
- `GET /api/recovery-cases/lost-reports/{lostReportId}` admin only.
- `POST /api/admin/recovery-cases` admin only. Creates a Lost Report first, then creates the linked Recovery Case.
- `POST /api/admin/recovery-cases/lost-reports/{lostReportId}` admin only. Creates or confirms a Recovery Case for an existing Lost Report.
- `POST /api/recovery-cases/lost-reports/{lostReportId}/refresh` admin only.
- `PATCH /api/recovery-cases/{id}` admin only.
- `POST /api/recovery-cases/{id}/assign` admin only.
- `GET /api/recovery-cases/{id}/missions` admin only.
- `POST /api/recovery-cases/{id}/missions` admin only.
- `PATCH /api/recovery-missions/{id}` admin only.

Recovery Cases must reference real Lost Reports. Lost Report creation never creates Found Items.

## Proof Vault

- `GET /api/items/{id}/proof-vault` admin only.
- `GET /api/claims/{claimId}/evidence-review` admin only.
- `POST /api/claims/{claimId}/evidence-review` admin only.

## Custody Ledger

- `GET /api/custody/items/{foundItemId}`
- `GET /api/custody/items/{foundItemId}/verify`
- `POST /api/custody/items/{foundItemId}/move` admin only.

## Event Recovery

- `GET /api/campus-zones`
- `GET /api/event-hubs`
- `GET /api/event-hubs/{id}`
- `GET /api/event-hubs/{id}/display-feed`
- `POST /api/admin/event-hubs` admin only.
- `PATCH /api/admin/event-hubs/{id}` admin only.
- `POST /api/admin/event-hubs/{id}/activate` admin only.
- `POST /api/admin/event-hubs/{id}/close` admin only.

## Asset Rescue

- `GET /api/assets/lookup?tag={assetTag}`

## Return Pass

- `POST /api/claims/{claimId}/return-pass` admin only.
- `GET /api/return-passes/{id}`
- `POST /api/return-passes/verify`
- `POST /api/return-passes/{id}/redeem` admin only.

## Pattern Review Powered By Loss Sentinel

- `GET /api/sentinel/alerts` admin only.
- `POST /api/sentinel/recompute` admin only. Analyzes actual Lost Reports only. Returns `state: "not_enough_data"` without saving an alert unless the same zone/category has at least 3 recent reports, at least 2 baseline reports, and recent volume at least 2x the normalized baseline.
- `PATCH /api/sentinel/alerts/{id}` admin only.
- `POST /api/sentinel/alerts/{id}/acknowledge` admin only.
- `POST /api/sentinel/alerts/{id}/dismiss` admin only.
- `POST /api/sentinel/alerts/{id}/resolve` admin only.
- `GET /api/sentinel/alerts/{id}/source-reports` admin only.
- `POST /api/sentinel/alerts/{id}/mission` admin only. Creates a real Recovery Mission from an alert source Lost Report.

Alerts store source Lost Report IDs, observed count, baseline count, recent/baseline date windows, reasons, suggested actions, and calculation timestamp.

## Admin Demo Scenarios

- `POST /api/admin/demo-scenarios/airpods_gym` admin only.
- `POST /api/admin/demo-scenarios/gym_electronics_pattern` admin only.
- `POST /api/admin/demo-scenarios/library_water_bottle` admin only.
- `POST /api/admin/demo-scenarios/custom` admin only.

Scenario records are normal Mongo records with `is_demo: true`; scenarios never delete non-demo user data.

## Partner Relay

- `GET /api/recovery-nodes` returns seeded demo nodes.
- `GET /api/partner-relays` returns redacted integration-ready relay summaries.

## Privacy Rules

Public-facing routes must not expose `private_verification_clues`, `storage_location`, `department_destination`, finder identity, claimant evidence, return-pass tokens, internal custody notes for public users, or restricted asset records.
