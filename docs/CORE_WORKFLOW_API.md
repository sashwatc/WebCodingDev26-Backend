# Core Workflow API

Actual lost-and-found lifecycle contract for the frontend (`appClient`). All JSON
is `snake_case`. Admin-gated routes require the demo `X-Demo-User-Email` header that
resolves to `ADMIN_EMAIL`. Base URL is `/api` (Vite proxy or `VITE_API_URL`).

## Lifecycle Invariants

1. Found Items are physical inventory and the source of truth for what can be claimed.
2. Lost Reports never create Found Items (they only open/refresh a Recovery Case).
3. Claims must reference an existing Found Item and require admin review before approval.
4. Match suggestions are advisory; they never change Found Item or Claim state.
5. A Found Item becomes `returned`/`ARCHIVED` only after an **approved** claim **and** a
   completed pickup. A claim cannot jump to `completed` from a non-approved state.
6. Public responses never expose storage location, private verification clues, finder/claimant
   private contact, proof-vault data, or Return Pass tokens.
7. Recovery Center counts are derived only from persisted cases, missions, and claims.

## Found Items (inventory)

Public:
- `GET /api/items` — list publicly visible items (`PublicFoundItemResponse[]`).
- `GET /api/items/{id}` — single public item; 404 if restricted/non-public.
- `POST /api/items` — create intake (status defaults to `FOUND`). Body: `title`, `category`,
  `location_found`, `date_found` (YYYY-MM-DD) required; optional `description`, `color`,
  `brand`, `subcategory`, `photo_urls`, `tags`, `event_hub_id`, `campus_zone_id`.
- `PATCH /api/items/{id}` — partial update; also supports `upsert_rating` / `remove_rating_by_claim_id`.
- `DELETE /api/items/{id}` — hard-delete if unreferenced, otherwise soft-archive.

Admin:
- `GET /api/admin/items` — full `FoundItem` records including private fields. **Admin only.**
- `POST /api/admin/items/{id}/archive` — archive a resolved item. **Admin only.**

Public fields (`PublicFoundItemResponse`): `id`, `title`, `description`, `category`,
`subcategory`, `color`, `brand`, `location_found`, `date_found`, `time_found`, `status`,
`record_type`, `created_date`, `updated_date`, `condition`, `priority`, `event_hub_id`,
`campus_zone_id`, `photo_urls`, `tags`.

Private fields (admin only, never in public DTO): `storage_location`,
`private_verification_clues`, `finder_email`, `finder_role`, `department_destination`,
`asset_tag`, `asset_record_id`, `restricted_visibility`, `claim_confirmed*`, `ratings`.

Status values: `FOUND` → `CLAIM_PENDING` → `VERIFIED` → `ARCHIVED`/`returned`.
Only `FOUND`, `CLAIM_PENDING`, and `approved` (legacy alias of `FOUND`) are publicly visible.

## Lost Reports

- `GET /api/entities/LostReport` — list (admin views via `GET /api/admin/lost-reports`).
- `POST /api/entities/LostReport` — create. Body: `title`/`item_type`, `category`,
  `contact_email` required; optional `description`, `color`, `brand`, `last_seen_location`,
  `date_lost`, `urgency`, `photo_url`, `student_id`, `extra_notes`. On save the backend
  ensures one Recovery Case and refreshes advisory matches. **No Found Item is created.**
- `PATCH /api/entities/LostReport/{id}` — partial update; re-runs match refresh.

`matched_items` is an advisory list (`found_item_id`, `confidence`, `reasons`, `source`).

## Claims

- `POST /api/entities/Claim` — create. Body: `found_item_id`, `claimant_name`,
  `claimant_email`, `reason`/`claim_reason`, `identifying_details` required; optional
  `proof_photo_url`, `pickup_availability`, `student_id`. New claims must be `submitted`
  (cannot be created already `approved`/`completed`). The referenced Found Item must exist,
  and the item moves to `CLAIM_PENDING`.
- `GET /api/claims/mine` — signed-in claimant-only list for the resolved backend identity.
  Used by the user dashboard instead of client-filtering the private global Claim list.
- `GET /api/entities/Claim` — admin-only because Claim records contain private evidence/contact fields.
- `PATCH /api/entities/Claim/{id}` — admin required for privileged workflow fields (`status`,
  `review_status`, `admin_notes`, `reviewed_*`, `received_confirmed_at`). Non-privileged
  claimant feedback fields remain validated by service rules.
- `DELETE /api/entities/Claim/{id}` — admin-only.

Admin review:
- `GET /api/admin/claims` — list claims. **Admin only.**
- `POST /api/admin/claims/{id}/approve` — approve; sets claim `approved`, item `VERIFIED`. **Admin only.**
- `POST /api/admin/claims/{id}/deny` — deny; reverts item to `FOUND` if no other pending claim. **Admin only.**

Claim statuses: `submitted` → `under_review`/`need_more_info` → `approved` → `completed`,
or `rejected`. Transition rules enforced server-side:
- Only one `approved`/`completed` claim per Found Item.
- A claim may only become `completed` from `approved` (or already `completed`). Completing
  marks the item `returned` and records `received_confirmed_at`.

Private claim fields (never public): `identifying_details`, `proof_photo_url`, `risk_score`,
`risk_flags`, `admin_notes`, `reviewed_by`, claimant contact.

## Matches (advisory only)

- `GET /api/matches/lost-reports/{lostReportId}` — suggestions for a report.
- `POST /api/matches/lost-reports/{lostReportId}/refresh` — recompute suggestions.
- `POST /api/matches/found-items/{foundItemId}/refresh` — recompute suggestions.

Suggestion fields: `found_item_id`, `found_item_title`, `confidence`, `reasons`, `source`,
`status`. Suggestions never mutate inventory or claim state.

## Recovery Cases / Missions

- `GET /api/recovery-cases` / `GET /api/recovery-cases/{id}` — **Admin only.**
- `GET /api/recovery-cases/lost-reports/{lostReportId}` — case for a report. **Admin only.**
- `POST /api/admin/recovery-cases` and `.../lost-reports/{lostReportId}` — create. **Admin only.**
- `POST /api/recovery-cases/lost-reports/{lostReportId}/refresh` — refresh plan/missions. **Admin only.**
- `PATCH /api/recovery-cases/{id}`, `POST .../assign`, mission CRUD under `.../missions`. **Admin only.**
- `GET /api/admin/recovery-center` — summary + case list. **Admin only.**

Recovery Center summary (`RecoveryCenterSummary`) is computed from persisted records:
`active_cases`, `open_missions`, `claims_awaiting_review`, `pickup_ready_cases`.
Case status flow: `open` → `match_identified` → `claim_in_review` → `pickup_ready` →
`returned` → `closed` (`paused`/`archived` are side states).

## Return Workflow (pickup)

- `POST /api/claims/{claimId}/return-pass` — issue a pass. Requires an `approved` claim and a
  `VERIFIED`/`claimed` item. Body: optional `pickup_window`, `pickup_location`. Rejects a second
  `active` pass for the same claim. On success records a `pickup_ready` custody event, moves the
  Recovery Case to `pickup_ready`, and notifies the claimant (generic copy, no code). **Admin only.**
- `GET /api/return-passes/{id}` — claimant (own pass) or admin only. A blank/unresolved caller is
  denied even if the pass has no claimant email, so access fails safely.
- `POST /api/return-passes/verify` — body `one_time_code`; manual verification step returning only
  `valid`, `return_pass_id`, `status`, `found_item_id`, `claim_id`, `message`. Never returns the
  code or token. Inactive or expired passes return `valid: false`.
- `POST /api/return-passes/{id}/redeem` — body `one_time_code` (must match the pass). One-time and
  atomic as far as the architecture permits: the pass is flipped to `redeemed` first (guarding
  against double redemption), then claim → `completed`, item → `ARCHIVED`/returned, custody
  `handoff_verified` + `returned` events, Recovery Case → `returned`, and an `item_returned`
  notification. A pass that is not `active` (already `redeemed`/`expired`/`cancelled`) or past
  `expires_at` cannot complete pickup. **Admin only.**
- `POST /api/return-passes/{id}/reminder` — resend pickup reminder (active passes only). **Admin only.**

Return Pass response (`ReturnPassResponse`) fields: `id`, `claim_id`, `found_item_id`,
`claimant_email`, `pickup_window`, `pickup_location`, `status`, `one_time_code`, `expires_at`,
`redeemed_at`, `redeemed_by`, timestamps. The `one_time_code` is only returned to the
authenticated claimant (own pass) and staff pickup flow. The internal `token` is **never**
returned. `verify` returns only `valid`, `return_pass_id`, `status`, `found_item_id`,
`claim_id`, `message`.

## Event Recovery / Demo Scenarios

See `docs/EVENT_RECOVERY_API.md` for Event Hub, QR Beacon, and admin demo scenario endpoints.
Those flows preserve the same core invariants: public event feeds return `PublicFoundItemResponse`
only, QR Beacons do not use GPS, Lost Reports do not create Found Items, and demo cleanup deletes
only records flagged `is_demo=true`.

## End-to-End State Transition

1. Item turned in → `POST /api/items` → `FOUND`.
2. Student loses item → `POST /api/entities/LostReport` (opens Recovery Case; advisory matches).
3. Student claims a Found Item → `POST /api/entities/Claim` → item `CLAIM_PENDING`.
4. Admin reviews → approve → claim `approved`, item `VERIFIED`.
5. Admin issues Return Pass → pass `active`.
6. Pickup: redeem pass (or admin completes the approved claim) → claim `completed`,
   item `returned`/`ARCHIVED`, Recovery Case `returned`.
