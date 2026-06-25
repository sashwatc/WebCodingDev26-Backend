# AI Agent Guardrails

These rules are permanent project instructions for AI agents working in this Spring Boot backend repo.

## Scope And Safety
- Do not edit secrets, `.env`, `.env.local`, credentials, tokens, provider keys, or deployment secrets.
- Do not install packages or add dependencies unless the user explicitly asks.
- Do not commit or push automatically. Only commit or push when the user explicitly asks in the current turn.
- Do not change application behavior while doing documentation, planning, review, or baseline tasks.

## Product Invariants
- Found Items are inventory records for physical items already turned in.
- Lost Reports are separate student reports and must not fabricate inventory.
- Claims require admin review before an item is considered verified, returned, or resolved.
- Do not create fake metrics, fake backend-owned records, or frontend-only workflow state for dashboards, recovery cases, sentinel alerts, delivery records, custody, or return passes.
- Do not expose private evidence, storage location, finder private contact, claimant private contact, proof vault data, or return-pass codes/tokens/instructions in public UI or public API responses.

## Backend Rules
- Keep controllers thin and place workflow rules in services.
- Preserve Spring JSON `snake_case` contract unless the frontend `appClient` and docs are updated together.
- Treat `X-Demo-User-Email` as demo-grade identity only; do not expand admin capability without explicit authorization checks.
- Do not let Lost Reports create Found Items unless a real found-item intake path is used.
- Keep seed/demo data clearly marked and never wipe non-demo user data.
- Preserve validation, sanitization, accessibility-supporting payload fields, and responsive frontend contracts.

## Required Checks
- Backend: `./mvnw test`
- Backend package check when needed: `./mvnw clean package`
- Frontend integration check when backend contracts change: run `npm run lint` and `npm run build` from `../WebCodingDev26`.
- Do not run destructive commands or reset user changes.
