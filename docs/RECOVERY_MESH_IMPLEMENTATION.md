# Recovery Mesh Implementation Tracker

## Existing Architecture Discovered

- Spring Boot backend with MongoDB repositories and snake_case JSON configured globally.
- Public item inventory currently uses `FoundItemController` and `FoundItemService`.
- Lost reports, claims, notifications, and audit logs are exposed through generic `/api/entities/{EntityName}` routes.
- Matching is deterministic local scoring with optional server-side AI ranking. Suggestions are stored on `LostReport.matchedItems`.
- Demo auth persists `AppUser` records; admin role is determined by sign-in email. Advanced admin routes use `X-Demo-User-Email` and never trust request-body roles.
- Seed data is guarded by `app.seed.enabled` and only runs when `found_items` is empty.

## Files Likely To Change

- Backend models, repositories, services, and controllers under `src/main/java/com/FBLA/WebCodingDev26Backend`.
- Seed logic in `config/SeedDataConfig.java`.
- Targeted tests under `src/test/java/com/FBLA/WebCodingDev26Backend`.
- Documentation under `docs/` and `README.md`.
- Frontend integration in `/Users/charank/FBLASLC26/WebCodingDev26` after the backend contract is written.

## Compatibility Risks

- Existing frontend expects `/api/items` to list found items. This route must remain public-safe and backward compatible.
- Generic `/api/entities/Claim` status patches are still used by the frontend. Recovery case and return-pass state transitions must hook into claim/item updates without breaking the route.
- Existing demo seed IDs should continue to work while adding the connected NLC demo scenario.
- Private fields added to models must not leak through public item, event display, search, or relay responses.

## New Collections And Endpoints

- `recovery_cases`, `recovery_missions`
- `campus_zones`, `event_recovery_hubs`
- `custody_events`
- `asset_registry`
- `return_passes`
- `prevention_alerts`
- `recovery_nodes`, `partner_relays`

## Public/Private Data Boundaries

- Public found-item DTOs exclude private verification clues, storage location, finder identity, department destination, claimant data, private evidence, asset destinations, internal audit data, and return-pass tokens.
- Restricted asset items are excluded from public item lists, item details, event hub lists, display feeds, and relay summaries.
- Proof Vault and custody movement endpoints require admin demo authorization.
- Return-pass verification does not mutate state; redemption is a one-time admin pickup workflow.

## Phase Checklist

- [x] Phase 0: Inspect architecture, define tracker, protect compatibility.
- [x] Phase 1: Recovery Cases and Recovery Missions.
- [x] Phase 2: Proof Vault and Evidence Review.
- [x] Phase 3: Tamper-Evident Custody Ledger.
- [x] Phase 4: Event Recovery Mode, QR zones, public display feed.
- [x] Phase 5: Asset Rescue Bridge.
- [x] Phase 6: Return Pass and Pickup Station.
- [x] Phase 7: Pattern Review powered by Loss Sentinel.
- [x] Phase 8: Privacy-Preserving Partner Relay.
- [x] Phase 9: NLC documentation and sources.
- [x] Phase 10: Presentation-ready seed scenario.

## Current Status

Backend implementation and frontend integration are complete. Backend tests and frontend lint/typecheck/build passed.
