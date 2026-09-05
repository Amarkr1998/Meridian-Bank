# ADR-0004: Redis for Idempotency, Rate Limiting & Short-Lived Data

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Several cross-cutting concerns need fast, short-lived, key-based storage that doesn't belong in the
system of record: idempotency-key lookups on the hot path of every payment request, login rate
limiting, OTP codes, and short-lived risk signals. Using PostgreSQL for these would work but adds
unnecessary load and latency to the primary transactional store for data that is inherently
ephemeral.

## Decision

Use Redis for: idempotency-key lookups (`payment-service`), rate limiting (`api-gateway`,
`auth-service`), and OTP storage (`auth-service`, `customer-kyc-service`, `account-service`).
Redis is explicitly **not** used anywhere it could cause an incorrect financial balance — balances
and ledger state live only in PostgreSQL, with Redis never in the authoritative path for money.

> **Implementation note (Phase 9):** this ADR originally anticipated `fraud-risk-service` using
> Redis for "short-lived risk signals." In practice it doesn't use Redis at all: the fraud rule
> engine's velocity/frequency rules need a queryable history over windows up to 30 days — well
> beyond what belongs in an ephemeral cache — and the assessment history itself needs to be
> durable and auditable (it's the input to every future scoring decision, not a disposable
> signal). It's stored in PostgreSQL instead (`risk_assessments`, append-only) — see
> [services/fraud-risk-service/README.md](../../services/fraud-risk-service/README.md). This is a
> scoping correction, not a reversal of this ADR's core decision (Redis still owns the genuinely
> ephemeral concerns below).

## Alternatives Considered

- **PostgreSQL for everything** (including idempotency keys and rate-limit counters) — rejected:
  works, but adds write load to the primary financial database for data with no long-term retention
  requirement, and is slower for the very-high-read-frequency rate-limit check.
- **In-memory (per-instance) caching** for rate limiting — rejected: doesn't work correctly once a
  service has more than one replica, since limits/idempotency state must be shared across instances.

## Trade-offs

Gains: low latency for hot-path checks, natural TTL support for OTPs/rate windows, doesn't burden
the transactional database. Costs: another operational component; Redis is not durable by default,
so its use is deliberately restricted to data where losing it is safe (rate-limit counters reset,
OTPs can be re-issued) — never balances or ledger state.

## Consequences

- Idempotency-key results are cached in Redis but the authoritative payment/transaction record still
  lives in PostgreSQL (`payment-service`); Redis is a lookup accelerator, not the source of truth for
  whether a payment happened.
- Consistency decisions per use case are documented alongside the owning service as it's
  implemented (Phase 2 for auth/rate-limiting/OTP, Phase 6 for payment idempotency).
