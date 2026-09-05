# ADR-0014: Circuit Breaker / Resilience Policy

**Status:** Accepted
**Date:** 2026-09-04

## Problem

`payment-service` makes several synchronous calls per request (`account-service`,
`customer-kyc-service`, `fraud-risk-service`, `ledger-service`). If any downstream service is slow or
down, naive synchronous calls without timeouts/circuit breaking can exhaust caller threads and cause
cascading failure across the platform — and naive blind retries on a financial write are actively
dangerous (see [ADR-0006](0006-idempotency.md)).

## Decision

Apply Resilience4j policies to inter-service calls: timeouts on every synchronous call, circuit
breakers around each downstream dependency to fail fast once it's unhealthy, and bulkheads where a
dependency's failure should not exhaust resources needed by unrelated request paths. Retry policies
are applied **only** where safe — read-only calls, and financial calls specifically because they are
protected by the `Idempotency-Key` mechanism ([ADR-0006](0006-idempotency.md)). Financial writes are
never retried without an idempotency key.

## Alternatives Considered

- **No resilience policies (bare synchronous calls)** — rejected: a single slow dependency would be
  free to cascade into full platform unavailability, which is unrealistic for a banking platform
  brief that explicitly calls out resilience as a demonstration goal.
- **Blanket automatic retry on all failures** — rejected: retrying a payment write that actually
  succeeded server-side but timed out on the client would create a duplicate transaction without the
  idempotency safeguard in place; retry is only introduced where it's provably safe.

## Trade-offs

Gains: bounded failure blast radius, graceful degradation, an observable signal (circuit open/
closed) for the observability stack. Costs: added configuration surface per dependency; a circuit
breaker tuned too aggressively can reject requests during a brief, recoverable blip.

## Consequences

- Every synchronous inter-service call in `payment-service` (and other services making sync calls)
  is wrapped with an explicit timeout and circuit breaker, not left to default HTTP client behavior.
- Circuit breaker state is exposed via Actuator/Micrometer for Prometheus/Grafana visibility
  (Phase 16).
- Implemented as each synchronous call is introduced (from Phase 6 onward), formalized as a
  cross-cutting concern once Resilience4j is wired in.
