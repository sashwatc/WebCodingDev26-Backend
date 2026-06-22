# PVHS Recovery Mesh

PVHS Recovery Mesh extends Lost Then Found into a school operations workflow for recovering physical inventory. Found Items remain the source of truth. Lost Reports are requests. Claims remain admin-reviewed.

## Implemented Workflows

- Recovery Cases: one case per Lost Report, with deterministic likely-zone planning and staff missions.
- Proof Vault: admin-only private clues and claimant evidence review. Scores and flags are advisory only.
- Tamper-Evident Custody Ledger: append-only custody events with SHA-256 chain verification.
- Event Recovery: public Event Hub, Beacon, and Display feeds using public-safe data only.
- Asset Rescue Bridge: seeded asset adapter with restricted visibility for recognized school property.
- Return Pass: approved claim to active pickup pass to one-time pickup redemption.
- Loss Sentinel: deterministic spike alerts with minimum sample and 2x-baseline guardrails.
- Partner Relay: redacted, simulated cross-location relay summaries.

## Data Boundaries

Public APIs exclude storage location, finder identity, private verification clues, department destinations, claimant evidence, pass tokens, and restricted assets. Admin-only routes use `X-Demo-User-Email` and resolve role from persisted users.

## Demo/Integration-Ready Limits

Event hubs, beacons, display mode, asset lookup, and partner relays are working demo adapters. They do not claim live PVHS calendar, locker, GPS, surveillance, TV, student schedule, or asset-system integration.
