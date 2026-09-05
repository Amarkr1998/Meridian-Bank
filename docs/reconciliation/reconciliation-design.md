# Reconciliation Design

> **Implementation status (Phase 12):** real and implemented inside `ledger-service` itself — not
> a separate microservice; CLAUDE.md's fixed 9-service list has none, and `ledger_entries` (the
> internal source of truth this compares against) already lives here. A scheduled
> `ReconciliationJob` genuinely compares every real ledger transaction against a synthetic external
> counterpart and classifies `MATCHED`/`MISMATCHED`/`PENDING`, never mutating `ledger_entries`.
> `PENDING` → `MATCHED` is a real transition driven by wall-clock time (a synthetic "delayed
> settlement"), not simulated. The staff `INVESTIGATION`/`RESOLVED` workflow and its RBAC are real,
> gated behind `OPERATIONS`/`ADMIN`, and each mismatch/resolution genuinely publishes `audit.event`
> to the real `audit-service` (Phase 11). As of Phase 15, the Ops Portal's Reconciliation screen
> (`/ops/reconciliation`) is real too — the "Dashboard" section below describes the general design
> intent, not a literal spec, but the actual screen calls this backend API directly (queue,
> filter by status, claim/investigate, resolve); see
> [services/ledger-service/README.md](../../services/ledger-service/README.md) and
> [frontend/README.md](../../frontend/README.md).

Sequence flow: [docs/architecture/reconciliation-flow.md](../architecture/reconciliation-flow.md).

## Purpose

Demonstrate a realistic reconciliation control: comparing Meridian's internal double-entry ledger
against an independent (synthetic) external record of the same transactions, surfacing mismatches
for investigation rather than assuming the ledger is always correct.

## Design

- **Internal source of truth:** `ledger_entries` in `ledger-service`.
- **External source:** a synthetic external transaction feed generated for demo purposes — never
  real external bank data or a live third-party integration.
- **Comparison job:** a scheduled batch process (see the Phase 12 batch/scheduled processing scope
  in [CLAUDE.md](../../CLAUDE.md)) pairs internal and external records by reference/amount and
  classifies each pair.
- **Never mutates the ledger.** Reconciliation only ever writes to `reconciliation_records`; the
  ledger stays append-only and authoritative.

## Statuses

| Status | Meaning |
|---|---|
| `MATCHED` | Internal and external records agree |
| `MISMATCHED` | Amount, reference, or status disagree between internal and external |
| `PENDING` | No external counterpart found yet (timing difference) |
| `INVESTIGATION` | An operations user has picked up a mismatch for review |
| `RESOLVED` | Investigation concluded, resolution notes recorded |

## Dashboard

The operations reconciliation dashboard (`/ops/reconciliation`) surfaces: total records, matched,
mismatched, pending, resolved — with drill-down into individual mismatched records for
investigation. See [docs/architecture/reconciliation-flow.md](../architecture/reconciliation-flow.md).

## Planned Implementation Phase

Phase 12 (Reconciliation + Batch Processing) — done, see the implementation-status note above.
