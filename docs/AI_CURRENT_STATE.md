# AI Current State - Backend

## Architecture
- Java 21 + Spring Boot 4.1 + Spring Web MVC + Bean Validation + Spring Data MongoDB.
- Build is Maven via `./mvnw`; config is in `src/main/resources/application.properties`.
- JSON uses `snake_case`; Mongo uses `MONGO_URI` and `MONGO_DATABASE`.
- Core layers are controllers, services, repositories, DTOs, and models.

## Run Commands
- Backend dev: `./mvnw spring-boot:run`
- Tests: `./mvnw test`
- Package: `./mvnw clean package`
- Optional npm aliases in this repo delegate to Maven.

## API Base URL
- Local backend runs on `http://localhost:8080`.
- Frontend calls it through `/api` via Vite proxy or a deployed `VITE_API_URL` origin.

## Completed Flows
- Controllers exist for health, auth, found items, admin dashboard, generic entities, matching, recovery cases/missions, sentinel, recovery pulse, return passes, proof vault, partner relay, event hubs, custody, assets, uploads, and demo scenarios.
- Services exist for auth, found items, admin workflow, matchmaking, recovery planning/cases, sentinel, pulse delivery, return passes, custody, assets, uploads, and seed/demo data.
- Tests cover API integration, auth, generic entities, matchmaking, recovery mesh rules, recovery pulse dispatch, seed data, and app context.
- Event Recovery API: public-safe Event Hub DTOs, display feed with redacted Found Items, campus zones for QR Beacon UI, admin hub management, and demo scenario builder. See `docs/EVENT_RECOVERY_API.md`.
- Optional assistance API: deterministic, editable found-item/search suggestions with optional server-side local Ollama.
- Integration audit verified API wiring for health, auth, public items, lost/found reporting, claims, admin review, proof vault, return passes, Recovery Pulse, Event Hub/Beacon, uploads, and optional assistance.

## Auth & Authorization
- Appwrite JWTs are verified server-side via `AppwriteAuthService` (replays the token to Appwrite `/account`; admin from `/teams`). No new dependencies, no API key, no passwords stored.
- `DemoAuthorizationService` resolves identity JWT-first; the `X-Demo-User-Email` fallback is gated by `app.auth.demo-fallback-enabled` (set `AUTH_DEMO_FALLBACK_ENABLED=false` in prod). Admin enforced server-side on all admin routes. See frontend `docs/AUTH_INTEGRATION.md`.
- `GET /api/auth/me` returns the verified backend user. Invalid JWTs are rejected and never fall back to the demo header.

## Broken Or Risky Flows
- Admin team membership requires `VITE_APPWRITE_ADMIN_TEAM_ID`; without it, admin falls back to the `ADMIN_EMAIL` match (demo only).
- Generic entity CRUD remains alongside feature-specific endpoints.
- Notification providers default to mock/demo behavior unless configured.
- Case-message endpoints are missing even though the frontend has `claimCaseMessages` hooks/UI.
- Full local browser E2E was not run because no backend was listening on `localhost:8080`, and starting one may write seed/demo data to an unknown Mongo target.

## Core Workflow Audit (Rules 1-7)
- Enforced: claims cannot reach `completed` (item `returned`) without a prior `approved` state (`WorkflowService.validateClaim`). Fixes Rule 5 bypass where a `submitted`/`under_review` claim could mark an item returned with no approval or pickup.
- Verified already holding: Lost Reports never create Found Items (Rule 2); claims must reference existing inventory (Rule 3); matches are advisory (Rule 4); public DTOs hide private fields and Return Pass token (Rule 6); Recovery Center counts come from persisted records (Rule 7).
- See `docs/CORE_WORKFLOW_API.md` for the full endpoint/state contract.

## Return Pass / Pickup Workflow
- Lifecycle (`ReturnPassService`): approved Claim → admin issues `active` Return Pass (custody `pickup_ready`, Recovery Case `pickup_ready`, claimant notified) → manual `verify` of the one-time code → admin `redeem` with the matching code → pass `redeemed`, claim `completed`, item `ARCHIVED`/returned, custody `handoff_verified` + `returned`, Recovery Case `returned`, `item_returned` notification.
- Redemption is single-use: the pass is flipped to `redeemed` before downstream saves, so a second redeem (or a `redeemed`/`expired`/`cancelled`/expired-by-time pass) fails with a conflict. Mismatched codes are rejected before any state change.
- Read access is claimant-own-or-admin; unauthenticated callers are denied even when a pass lacks claimant email. Codes never appear in notifications/public verify responses. Return Passes are not exposed through generic entity CRUD.

## Event Recovery / Demo Workflows
- Public Event Hub routes return public-safe event context only and never claim live PVHS calendar integration.
- QR Beacon is frontend route-based: no GPS; zone/event IDs prefill report forms and remain editable.
- Admin Demo Builder templates create real persisted records with `is_demo=true`: AirPods at Gym, Approved Calculator Return, Gym Electronics Pattern. Cleanup requires exact confirmation and deletes only demo-flagged records.

## Optional AI Assistance
- `POST /api/ai-assistance/found-item` suggests editable category/color/brand/tags from public item title/description/photo metadata only.
- `POST /api/ai-assistance/search` parses natural-language search into editable filters/keywords.
- Ollama is optional (`AI_ASSISTANCE_OLLAMA_ENABLED=false` by default); deterministic fallback works without it. No Proof Vault/private evidence is sent and no claim/ownership approval is automated.

## Current Next Task
- Implement backend case-message endpoints or disable/remove the frontend Case Messages UI until the contract exists.

## Last Test Status
- `./mvnw test`: 80 passing after integration audit fixes.
