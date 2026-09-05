# ADR-0007: Double-Entry Ledger

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Representing an account's state as a single mutable `balance` column that is incremented/decremented
by transfers is how naive banking demos are built — and it's wrong. It cannot represent where money
came from or went, makes reconciliation impossible, offers no audit trail of how a balance was
reached, and provides no structural guarantee that money isn't created or destroyed by a bug.

## Decision

Implement a real double-entry ledger in `ledger-service`. Every successful financial transaction
produces a balanced set of `ledger_entries` (at minimum one `DEBIT` and one matching `CREDIT`, equal
in amount) referencing the originating `transactionId`. Account balances are **derived** from ledger
entries (materialized into `balances` for read performance, but the ledger entries are the source of
truth). See [docs/architecture/payment-flow.md](../architecture/payment-flow.md).

## Alternatives Considered

- **Single mutable balance column** (`balance = balance - amount`) — rejected outright: no audit
  trail, no structural guarantee of conservation of money, cannot support reconciliation or
  reversal/refund modeling cleanly.
- **Event-sourced balance with no explicit debit/credit typing** — a partial improvement over a bare
  balance column, but double-entry accounting is the established, well-understood discipline for
  this exact problem and is a stronger, more legible demonstration of banking domain knowledge.

## Trade-offs

Gains: auditable, reconciliable, structurally resistant to money being created/destroyed by a bug
(every entry has a matching counter-entry), natural fit for reversal/refund as new balancing entries
rather than mutation. Costs: more complex than a balance column; requires care in transaction
boundaries and concurrency control to keep postings atomic (see
[ADR-0008](0008-concurrency-strategy.md)).

## Consequences

- `ledger_entries` is append-only; corrections are new entries, never edits or deletes.
- Reversal/refund transactions post the inverse entry pair rather than mutating the original.
- Reconciliation (see [ADR-0013](0013-reconciliation.md)) compares against this ledger as the
  internal source of truth.
- Implemented in Phase 7, alongside the concurrency strategy that protects concurrent postings.
