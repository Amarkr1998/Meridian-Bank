# ADR-0013: Ledger Reconciliation Against Synthetic External Records

**Status:** Accepted
**Date:** 2026-09-04

## Problem

A ledger that is never independently checked against another record of the same activity is trusted
on faith. Real banking systems reconcile internal records against external ones (correspondent
banks, card networks, clearing houses) to catch discrepancies caused by bugs, timing differences, or
integration failures. Meridian Bank has no real external banking network to reconcile against, but
should still demonstrate the reconciliation *pattern*.

## Decision

Implement a reconciliation job that compares `ledger-service`'s internal `ledger_entries` against a
synthetic, locally-generated external transaction feed, classifying each pairing as `MATCHED`,
`MISMATCHED`, `PENDING`, and supporting operations investigation through `INVESTIGATION` →
`RESOLVED`. Reconciliation only ever writes to `reconciliation_records` — it never mutates the
ledger. See [docs/reconciliation/reconciliation-design.md](../reconciliation/reconciliation-design.md).

## Alternatives Considered

- **No reconciliation** — rejected: skips a core banking control pattern this project exists to
  demonstrate, and leaves no mechanism to surface a class of bug (silent ledger drift) that unit
  tests on individual transactions wouldn't catch.
- **Reconcile against a real external API/sandbox** — rejected per the project's safety rules: no
  real banking network integrations are permitted; a synthetic feed generated locally demonstrates
  the same comparison logic without that dependency.

## Trade-offs

Gains: demonstrates a real operational control pattern and gives the operations portal a genuine
investigative workflow. Costs: the "external" side is synthetic, so it cannot catch real integration
bugs — it is explicitly a pattern demonstration, documented as such.

## Consequences

- The reconciliation *backend* (`ReconciliationService`, `ReconciliationJob`, and the
  `GET`/`PATCH /api/v1/ledger/reconciliation-records` API) is a first-class Phase 12 deliverable.
  The `/ops/reconciliation` *dashboard* itself is a future-phase (15) frontend concern — what
  Phase 12 delivers is the real API such a dashboard would call, not the UI.
- Synthetic external records must be clearly marked as demo data (see
  [Safety & Realism Rules](../../README.md#safety--realism-rules)).

## Implementation notes (Phase 12)

- No new microservice: reconciliation lives inside `ledger-service` itself, alongside the
  `ledger_entries` it compares against — CLAUDE.md's fixed 9-service list has no separate
  reconciliation service.
- The synthetic feed (`ExternalFeedGenerator`) is a deterministic, reproducible function of each
  transaction's own id, not pure randomness — see
  [services/ledger-service/README.md](../../services/ledger-service/README.md) for the exact
  70/20/10 immediate-match/delayed-match/mismatch bands, and for the live-verified proof that
  `PENDING` → `MATCHED` is a genuine wall-clock-driven transition.
- This phase was also `ledger-service`'s first use of both the outbox pattern
  (`reconciliation.completed`) and the Phase 11 audit pattern (`RECONCILIATION_MISMATCH_DETECTED`,
  `RECONCILIATION_RESOLVED`).
