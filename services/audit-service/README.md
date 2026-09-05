# audit-service

**Status:** Implemented (Phase 11 — Audit + Data Governance).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`audit_service` database),
Kafka. See [pom.xml](pom.xml). No Redis, no Resilience4j, no outbound synchronous calls to any
other Meridian service — this service only consumes from Kafka and serves read-only queries.

## Responsibility

The platform's centralized, append-only audit trail — see
[docs/adr/0012-audit-architecture.md](../../docs/adr/0012-audit-architecture.md) and
[docs/architecture/kafka-architecture.md](../../docs/architecture/kafka-architecture.md).

- Consumes `audit.event` — a single Kafka topic every other producing service publishes to
  (via its existing transactional outbox, same pattern as its own domain events) whenever a
  sensitive action occurs.
- Stores each event as a durable `audit_events` row: `actorId`, `actorRole`, `action`,
  `resourceType`, `resourceId`, `result`, `detail`, plus the envelope's own `eventId`,
  `occurredAt`, `correlationId`, and `producedBy` — see docs/kafka/topics.md's envelope
  convention.
- Exposes a read-only, paginated, multi-filter query API for staff investigation.
- **There is no update or delete path anywhere in this service, for any role.** Ingestion happens
  exclusively through the Kafka consumer; the HTTP API is `GET` only (see `AuditEventController`
  and `AuditEventRepository`, and `SecurityConfig`'s Javadoc) — not merely undocumented, genuinely
  unimplemented, and proven so by a live test that a `POST` to the audit-events path (even with an
  `ADMIN` token) comes back `405 METHOD_NOT_ALLOWED`.

## This project's first genuine Kafka consumer

Every prior phase's Kafka work (Phase 8, and `fraud-risk-service` in Phase 9) was producer-only —
see [docs/kafka/topics.md](../../docs/kafka/topics.md)'s Phase 8/9 status note: events were
"durably on the topic, ready to be consumed once those services exist." This is that service.
`AuditEventListener` (`@KafkaListener(topics = "audit.event")`, its own consumer group
`audit-service` — see `application.yml`) hands each record to `AuditIngestService`, which:

- **Is idempotent by the envelope's `eventId`**, not this table's own row id — Kafka delivery is
  at-least-once, not exactly-once, so a redelivered message (consumer restart after a commit race,
  a producer retry that also eventually succeeded, etc.) must not create a second row. Checked via
  `existsByEventId` and durably backstopped by a unique constraint on `event_id`, with a
  `DataIntegrityViolationException` from a lost race treated the same as a normal duplicate — see
  `AuditIngestService`'s Javadoc.
- **Parses the payload defensively**, matching docs/kafka/topics.md's additive-schema-evolution
  convention: a missing optional field defaults gracefully (e.g. `result: "UNKNOWN"`) rather than
  failing the whole message. Only a structurally-broken envelope (unparseable JSON, or a missing
  `eventId`/`eventType`/`producedBy`) is a hard failure.
- **Retries with exponential backoff, then dead-letters.** A thrown exception is caught by Spring
  Kafka's `DefaultErrorHandler` (see `AuditKafkaConfig`) — `meridian.audit-consumer.max-attempts`
  (default 5) total attempts, `base-backoff-ms`→`max-backoff-ms` (default 1s→30s) exponential
  backoff — mirroring the producer-side retry/backoff policy every outbox-using service already
  implements, on the opposite side of the topic. Exhausting the attempts routes the raw message,
  unmodified, to `audit.event.DLQ` via `DeadLetterPublishingRecoverer`, and the original offset is
  committed — a permanently unprocessable message (a genuine "poison pill," e.g. malformed JSON)
  does not block the partition forever.

## API

All endpoints under `/api/v1/audit-events`, restricted to `AUDITOR`/`COMPLIANCE_OFFICER`/`ADMIN`
(see [Governance Principles](../../docs/governance/governance-principles.md), "AUDITOR has
read-only access to audit data").

| Method & Path | Description |
|---|---|
| `GET /audit-events` | Paginated search, filterable by any combination of `actorId`, `action`, `resourceType`, `resourceId`, `producedBy`, `correlationId` |
| `GET /audit-events/{id}` | Single event detail |

There is no `POST`/`PATCH`/`DELETE` on this controller at all.

## What every producing service records (Phase 11)

Each of `customer-kyc-service`, `account-service`, `payment-service`, and `fraud-risk-service` now
publishes `audit.event` for its sensitive actions, via a small duplicated `audit` package (an
`AuditAction` enum, an `AuditEventPayload` record, and an `AuditEventPublisher` that's a thin
wrapper around that service's existing `OutboxWriter` — same duplication convention as the
maker-checker `approval` packages, ADR-0003):

| Service | Actions recorded |
|---|---|
| `customer-kyc-service` | `CUSTOMER_REGISTERED`, `KYC_SUBMITTED`, `KYC_APPROVED`, `KYC_REJECTED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED` |
| `account-service` | `ACCOUNT_CREATED`, `ACCOUNT_FROZEN`, `ACCOUNT_BLOCKED`, `BENEFICIARY_ADDED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED` |
| `payment-service` | `PAYMENT_INITIATED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED` |
| `fraud-risk-service` | `FRAUD_ALERT_CREATED`, `AML_ALERT_CREATED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED`, `CONFIGURATION_CHANGED` |

`APPROVAL_CREATED`/`APPROVAL_COMPLETED` are recorded by all four gating services (Phase 10) at
their maker-checker request/decision points — the `resourceId` is the approval request's own id,
and `result` carries `PENDING_APPROVAL`/`APPROVED`/`REJECTED`. `fraud-risk-service` additionally
records a distinct `CONFIGURATION_CHANGED` event on a fraud rule's `resourceId` when an approved
rule update actually applies — see
[Governance Principles](../../docs/governance/governance-principles.md) ("Business thresholds ...
audited (`CONFIGURATION_CHANGED`) independently of a code deployment").

**What's honestly not covered:** this is not every conceivable sensitive action in the system —
e.g. account freeze/close beyond the one `ACCOUNT_FROZEN` action, beneficiary block/unblock, and
KYC-adjacent profile updates are not individually audited yet. The four services' own READMEs
document exactly which of their endpoints emit an audit event today; this is a real, working
pipeline for the actions listed above, not a claim of exhaustive coverage.

## Known limitation: no funding source doesn't affect this service

Unlike `ledger-service`/`payment-service`, nothing here depends on account balances, so
`audit-service` has no analogous gap.

## Running locally

From the repository root: `docker compose up -d --build audit-service` (brings up `postgres` and
Kafka — waiting for the `kafka-init` topic bootstrap, which creates both `audit.event` and
`audit.event.DLQ`, to complete — automatically). Listens on `localhost:8087`
(`AUDIT_SERVICE_PORT` in `.env`). Health: `GET /actuator/health` (deliberately does not depend on
Kafka reachability — a broker outage should not flip this service unhealthy; the consumer simply
resumes from its last committed offset once Kafka returns).

## Tests

- `AuditIngestServiceTest` — unit tests (Mockito) for the ingestion logic in isolation: a new
  event is saved with all fields correctly extracted; an already-seen `eventId` is a no-op; a
  concurrent duplicate insert (unique-constraint race) is tolerated, not thrown; malformed JSON
  and a missing required envelope field both throw `MalformedAuditEventException`; and a payload
  missing optional fields still saves, with graceful defaults.
- `AuditEventConsumerIntegrationTest` — **the flagship proof for Phase 11**, against a real
  Postgres *and* a real Kafka broker (Testcontainers, not mocked, not calling the listener
  directly): a raw message published to the real `audit.event` topic is genuinely consumed by the
  running `AuditEventListener` and lands as a durable row; a redelivered message with the same
  `eventId` does not create a second row; and a structurally-broken message is retried (with the
  test's backoff shortened via `@SpringBootTest(properties = ...)`) and then genuinely observed
  landing on the real `audit.event.DLQ` topic via a raw consumer.
- `AuditEventControllerIntegrationTest` — full-stack test against real Postgres (Testcontainers),
  seeding rows directly via the repository (ingestion is covered separately, above). Covers RBAC
  (`AUDITOR`/`COMPLIANCE_OFFICER`/`ADMIN` can list and read; `CUSTOMER`/`OPERATIONS`/`RISK_ANALYST`
  get `403`; unauthenticated gets `401`), filtering by `actorId`, detail lookup with a `404` for an
  unknown id, and — the one specific to this service's whole design guarantee — a `POST` to
  `/audit-events` (even with an `ADMIN` token) comes back `405 METHOD_NOT_ALLOWED`, not `200`.

Verified end-to-end against the live ten-service stack (`postgres` + `redis` + `kafka` +
`auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service` + `fraud-risk-service` + `audit-service`): real sensitive actions performed
against the live services — customer registration, KYC submission/approval, account approval,
account blocking (maker-checker), a real payment, and a fraud alert — were each independently
confirmed to have landed as real rows in `audit-service`'s own database, queryable over real
RBAC-gated HTTP by a provisioned `AUDITOR` account, with a `CUSTOMER` token correctly rejected
`403` on the same endpoint — see the root [README.md](../../README.md#getting-started) for the
full transcript.
