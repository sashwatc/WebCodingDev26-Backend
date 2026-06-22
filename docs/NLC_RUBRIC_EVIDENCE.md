# NLC Rubric Evidence

## Style And Visual Design

- Restrained PVHS operations interface using existing cards, badges, tabs, forms, and dashboard vocabulary.
- Reusable status badges and text labels distinguish open, in-review, pickup-ready, returned, verified, and attention-needed states.
- Public pages keep Search / Report Lost / Report Found clear while advanced features stay in dashboards and detail views.

## Usability, Accessibility, Navigation

- HashRouter routes added: `/EventHub`, `/Beacon`, `/Display`, `/PickupPass`, `/PickupStation`.
- Manual fallback exists for beacons and pickup station code entry; no GPS or QR-only dependency.
- Existing skip link, route announcements, Radix primitives, visible focus states, dark mode, high contrast mode, dyslexic mode, and responsive nav are preserved.

## Coding Skills

- Spring Boot controllers/services/repositories for recovery cases, missions, proof vault, custody ledger, event hubs, assets, return passes, sentinel alerts, recovery nodes, and partner relays.
- Mongo indexes include unique recovery case per lost report and unique custody sequence per found item.
- Deterministic Recovery Planning and Loss Sentinel services avoid fake AI precision.
- Public DTOs redact private fields; admin endpoints use persisted demo user roles.
- Tamper-Evident Custody Ledger verifies SHA-256 event chains.
- React/TanStack Query integration is centralized through `src/api/appClient.js`.

## Compatibility And Reliability

- Backend tests: `./mvnw test` passed with 29 tests, 0 failures.
- Frontend checks passed: `npm run lint`, `npm run typecheck`, `npm run build`.
- Public fallback seed data supports demo mode when the backend is unavailable.
- Public Display/Event/Search routes exclude restricted assets and private fields.

## Sources

- React documentation: https://react.dev/
- Vite guide: https://vite.dev/guide/
- Spring Boot documentation: https://docs.spring.io/spring-boot/index.html
- MongoDB index documentation: https://www.mongodb.com/docs/manual/indexes/
- WCAG 2.2 Quick Reference: https://www.w3.org/WAI/WCAG22/quickref/
- WAI-ARIA Authoring Practices Guide: https://www.w3.org/WAI/ARIA/apg/
- Radix UI accessibility: https://www.radix-ui.com/primitives/docs/overview/accessibility

## Presentation Proof

The seeded demo path includes event beacon reporting, likely recovery zones, admin missions, proof vault review, custody verification, active return pass, pickup redemption, returned status, Loss Sentinel, and redacted Partner Relay.
