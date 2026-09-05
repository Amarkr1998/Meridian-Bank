# ADR-0006: Idempotency via Idempotency-Key

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Clients (and networks) retry. A payment request that times out on the client side but actually
succeeded on the server must not be reprocessed on retry — doing so would create a duplicate
financial transaction. This is one of the most consequential correctness requirements in a payments
system.

## Decision

Every payment-mutating endpoint requires a client-supplied `Idempotency-Key` header. On receipt,
`payment-service` looks the key up (Redis-backed, see [ADR-0004](0004-redis-for-idempotency-and-rate-limiting.md))
before running any validation. A new key proceeds through the normal pipeline and its result is
cached under that key. A previously-seen key short-circuits straight to the original stored result —
the request is never reprocessed. See
[docs/architecture/payment-flow.md](../architecture/payment-flow.md).

## Alternatives Considered

- **Client-side retry suppression only** (no server enforcement) — rejected: the server is the only
  place that can actually guarantee no duplicate processing; trusting the client is not a safety
  guarantee.
- **Natural-key deduplication** (e.g. dedupe on source+destination+amount+timestamp) — rejected:
  too fragile and ambiguous — legitimate repeated transfers (same amount, same parties, different
  intent) would be incorrectly collapsed.
- **Database unique constraint only, no cache** — a necessary complement (defends against races) but
  insufficient alone as the fast-path check, since it still requires attempting an insert; the
  Redis lookup exists to short-circuit before validation work happens.

## Trade-offs

Gains: safe retries, which in turn make Resilience4j retry policies (see
[ADR-0014](0014-circuit-breaker.md)) safe to use on the payment path at all. Costs: clients must be
disciplined about generating a stable key per logical operation (not per HTTP attempt), and the key
lookup adds a dependency on Redis availability on the payment hot path.

## Consequences

- `transactions.idempotency_key` carries a database-level unique constraint as the last line of
  defense, in addition to the Redis-backed fast path.
- Financial retries anywhere in the system (Resilience4j retry policy, client retry) are only
  considered safe because of this mechanism — retries without an idempotency key are prohibited on
  mutating financial endpoints.
- Implemented in Phase 6, with tests specifically covering duplicate-key replay.
