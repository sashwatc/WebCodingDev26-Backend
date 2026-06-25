# Lost Then Found Backend

Spring Boot API for the PVHS Lost Then Found recovery platform. It owns persisted lost-and-found records, workflow state, authorization checks, event recovery data, demo scenarios, Return Pass pickup, Recovery Center metrics, and public-safe DTOs used by the React frontend in `../WebCodingDev26`.

## Stack

- Java 21, Spring Boot 4.1, Spring Web MVC
- Bean Validation, Spring Data MongoDB
- Maven via `./mvnw`
- MongoDB configured with `MONGO_URI` and `MONGO_DATABASE`

## Run Locally

```bash
./mvnw spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/health
```

Tests:

```bash
./mvnw test
```

Optional npm aliases in this repo delegate to Maven.

## Configuration

Use environment variables or an ignored local env file. Never commit real secrets.

```env
MONGO_URI=fill_this_in
MONGO_DATABASE=lostthenfound
ADMIN_EMAIL=fill_this_in
AUTH_DEMO_FALLBACK_ENABLED=true
VITE_APPWRITE_ENDPOINT=
VITE_APPWRITE_PROJECT_ID=
VITE_APPWRITE_ADMIN_TEAM_ID=
SEED_DATA_ENABLED=true
AI_ASSISTANCE_OLLAMA_ENABLED=false
```

For production, configure Appwrite values and set `AUTH_DEMO_FALLBACK_ENABLED=false`. Ollama assistance is optional; when disabled or unavailable, deterministic suggestions are returned.

## Core API Areas

- Public Found Items: `GET/POST/PATCH/DELETE /api/items`, public responses use `PublicFoundItemResponse`.
- Lost Reports: `POST /api/entities/LostReport`; reports remain separate from Found Items.
- Claims: public `POST /api/entities/Claim`; user-scoped `GET /api/claims/mine`; admin-only Claim listing/deletion and privileged status/review updates.
- Admin review: `GET /api/admin/claims`, `POST /api/admin/claims/{id}/approve`, `POST /api/admin/claims/{id}/deny`.
- Recovery Center and missions: `/api/recovery-cases`, `/api/admin/recovery-center`, `/api/recovery-missions`.
- Return Passes: issue, gated read, verify, redeem, reminder under `/api/claims` and `/api/return-passes`.
- Event Hub and QR Beacon support: `/api/event-hubs`, `/api/campus-zones`, `/api/admin/event-hubs`.
- Admin Demo Builder: `/api/admin/demo-scenarios/*`; cleanup deletes only `is_demo=true` records.
- Optional assistance: `POST /api/ai-assistance/search` and `POST /api/ai-assistance/found-item`; suggestions are editable and never approve claims.

See `docs/CORE_WORKFLOW_API.md` and `docs/EVENT_RECOVERY_API.md` for request bodies, response fields, and state transitions.

## Authorization

The backend never accepts passwords. In Appwrite mode, callers send `X-Appwrite-JWT`; `AppwriteAuthService` verifies it against Appwrite `/account`, and `DemoAuthorizationService` derives admin access from Appwrite team membership. Local `X-Demo-User-Email` fallback is gated by `AUTH_DEMO_FALLBACK_ENABLED` and must be disabled in production.

Admin-only controllers call `DemoAuthorizationService.requireAdmin(...)`. Generic Claim routes also protect private Claim reads/deletes and privileged workflow fields.

## Workflow Guarantees

- Found Items are inventory records for physical items already turned in.
- Lost Reports never create Found Items.
- Claims must reference existing Found Items and cannot be created as approved/completed.
- Match suggestions are advisory and do not mutate inventory.
- AI/Ollama assistance is server-side only, optional, and falls back to deterministic parsing without confidence claims.
- Claims cannot move to completed unless already approved.
- Return Pass redemption is one-time; redeemed, expired, cancelled, or mismatched passes fail.
- Public APIs do not expose storage locations, private clues, proof vault data, finder/claimant private contact, or internal pass tokens.
- Recovery Center and Sentinel counts are derived from persisted records, not frontend-only metrics.
