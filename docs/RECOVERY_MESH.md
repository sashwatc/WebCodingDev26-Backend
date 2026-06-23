# PVHS Recovery Mesh

PVHS Recovery Mesh extends Lost Then Found into a school operations workflow for recovering physical inventory. Found Items remain the source of truth. Lost Reports are requests. Claims remain admin-reviewed.

## Implemented Workflows

- Recovery Cases: one case per Lost Report, with deterministic likely-zone planning and staff missions.
- Proof Vault: admin-only private clues and claimant evidence review. Scores and flags are advisory only.
- Tamper-Evident Custody Ledger: append-only custody events with SHA-256 chain verification.
- Event Recovery: public Event Hub, Beacon, and Display feeds using public-safe data only.
- Asset Rescue Bridge: seeded asset adapter with restricted visibility for recognized school property.
- Return Pass: approved claim to active pickup pass to one-time pickup redemption.
- Pattern Review powered by Loss Sentinel: deterministic Lost Report pattern alerts with minimum recent history, baseline history, source report IDs, and 2x-baseline guardrails.
- Partner Relay: redacted, simulated cross-location relay summaries.

## Data Boundaries

Public APIs exclude storage location, finder identity, private verification clues, department destinations, claimant evidence, pass tokens, and restricted assets. Admin-only routes use `X-Demo-User-Email` and resolve role from persisted users.

## Recovery Center Rules

Recovery Cases are linked to real Lost Reports. If an admin starts a case from scratch, the backend saves the Lost Report first and then creates the case. Lost Reports do not create Found Items; Found Items remain actual inventory records. Claim records remain separate and admin-reviewed.

The Recovery Center summary is calculated on the server from stored cases, missions, and claims: active cases, open missions, claims awaiting review, and pickup-ready cases. Mutating actions persist the changed record and write audit history where the audit repository is available.

## Pattern Review Rules

Pattern Review analyzes Lost Reports only. It returns `not_enough_data` when the data is too thin, and only saves an alert when a zone/category has at least 3 recent Lost Reports, at least 2 baseline Lost Reports, and recent volume at least 2x the normalized baseline. Saved alerts include source Lost Report IDs, observed and baseline counts, date windows, reasons, suggested actions, and calculation time.

## Demo/Integration-Ready Limits

Event hubs, beacons, display mode, asset lookup, and partner relays are working demo adapters. They do not claim live PVHS calendar, locker, GPS, surveillance, TV, student schedule, or asset-system integration.
