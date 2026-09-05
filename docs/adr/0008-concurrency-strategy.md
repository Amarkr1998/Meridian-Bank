# ADR-0008: Concurrency Strategy for Financial State

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Two concurrent transfers debiting the same account can race: both read the same starting balance,
both compute a sufficient-funds check against it, and both post — overdrawing the account past what
either check individually would have allowed. Financial correctness requires this race to be
structurally impossible, not merely unlikely.

## Decision

Use **optimistic locking** (a `version` column on `accounts`/`balances`) as the default concurrency
control for account/balance updates, since most concurrent access to a given account is rare enough
that optimistic conflict-and-retry is efficient. For the balance-sufficiency check and ledger posting
specifically — where a lost update directly risks overdrawing an account — combine this with a
database transaction using an appropriate isolation level, and use **pessimistic row locking**
(`SELECT ... FOR UPDATE`) on the source account's balance row for the duration of the debit check +
posting, since this is the one path where a failed optimistic retry under contention is unacceptable
(it must succeed correctly on the first serialized attempt, not merely detect a conflict after the
fact).

## Alternatives Considered

- **Optimistic locking only** — sufficient for most account field updates, but insufficient alone
  for the debit-check-then-post sequence: two optimistic transactions can both pass their
  sufficient-funds check against stale reads before either commits, unless isolation is raised or a
  row lock is taken.
- **Pessimistic locking on every account operation** — rejected as the default: unnecessarily
  serializes read-mostly operations (e.g. balance inquiry doesn't need a lock) and would hurt
  throughput without benefit outside the debit path.
- **Application-level distributed lock** (e.g. Redis lock per account) — rejected: the database
  transaction already provides the needed guarantee via row locking; adding a second lock layer
  duplicates responsibility and introduces its own failure modes (lock expiry vs. transaction
  duration mismatches).

## Trade-offs

Gains: correctness under concurrent transfers without serializing the entire system. Costs: row-level
locks on hot accounts can become a throughput bottleneck under high concurrent load on the same
account (acceptable at this project's demo scale); requires careful transaction boundary discipline
so locks are held for the shortest necessary scope.

## Consequences

- The debit-check-and-post sequence in `ledger-service` runs inside one transaction with a row lock
  on the source account.
- Other account field updates (e.g. status changes) use optimistic locking via the `version` column.
- Tests in Phase 7 specifically exercise concurrent transfers from the same source account to prove
  no overdraft is possible.
