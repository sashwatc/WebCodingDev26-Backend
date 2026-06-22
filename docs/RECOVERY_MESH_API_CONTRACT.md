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

- `GET /api/recovery-cases`
- `GET /api/recovery-cases/{id}`
- `GET /api/recovery-cases/lost-reports/{lostReportId}`
- `POST /api/recovery-cases/lost-reports/{lostReportId}/refresh`
- `PATCH /api/recovery-cases/{id}`
- `GET /api/recovery-cases/{id}/missions`
- `PATCH /api/recovery-missions/{id}`

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

## Loss Sentinel

- `GET /api/sentinel/alerts` admin only.
- `POST /api/sentinel/recompute` admin only.
- `PATCH /api/sentinel/alerts/{id}` admin only.

## Partner Relay

- `GET /api/recovery-nodes` returns seeded demo nodes.
- `GET /api/partner-relays` returns redacted integration-ready relay summaries.

## Privacy Rules

Public-facing routes must not expose `private_verification_clues`, `storage_location`, `department_destination`, finder identity, claimant evidence, return-pass tokens, internal custody notes for public users, or restricted asset records.
