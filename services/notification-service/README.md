# notification-service

**Status:** Implemented (Phase 13 — Notification + Customer Support).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`notification_service`
database), Kafka. See [pom.xml](pom.xml). No Redis, no Resilience4j, no outbound synchronous
calls to any other Meridian service, and no outbox of its own — this service only consumes from
Kafka and serves the notification inbox API.

## Responsibility

Customer notifications — see [docs/kafka/topics.md](../../docs/kafka/topics.md).

- **This project's second genuine Kafka consumer**, after [audit-service](../audit-service)
  (Phase 11). One `@KafkaListener` per consumed topic
  (`payment.completed`, `payment.failed`, `kyc.updated`, `account.approved`, `fraud.detected`,
  `notification.requested`), all under this service's own consumer group
  (`notification-service`), idempotent by the Kafka envelope's `eventId`, with retry/backoff and a
  real `<topic>.DLQ` on exhausted attempts — same policy shape as `audit-service`'s consumer, see
  `NotificationKafkaConfig`'s Javadoc.
- **In-app notifications are real and queryable** — every consumed event becomes a durable
  `Notification` row (`GET /api/v1/notifications`), markable as read.
- **Email and SMS delivery are simulated — logged only, never a real provider.** This service has
  no real email address or phone number for a customer (that lives in `customer-kyc-service`), so
  "delivery" for those two channels is necessarily simulated; see `NotificationIngestService`'s
  Javadoc. Never a paid provider integration (CLAUDE.md's non-negotiable safety rules).

## What's honestly reachable and what isn't

`NotificationType` defines 8 values, matching this service's original scaffold. **6 are genuinely
reachable** today — each has a real producer publishing the Kafka event this service consumes to
create one:

| Type | Triggered by |
|---|---|
| `PAYMENT_SUCCESS` | `payment.completed` (`payment-service`) |
| `PAYMENT_FAILED` | `payment.failed` (`payment-service`) |
| `KYC_STATUS_CHANGED` | `kyc.updated` (`customer-kyc-service`) |
| `ACCOUNT_STATUS_CHANGED` | `account.approved` (`account-service`) — the only account lifecycle event actually published to Kafka; freeze/block/close don't publish domain events |
| `FRAUD_ALERT` | `fraud.detected` (`fraud-risk-service`) |
| `SUPPORT_REQUEST_RESOLVED` | `notification.requested` (`customer-kyc-service`'s `SupportRequestService` — see its README's "Customer Support" section) |

**`LOGIN_ALERT` and `SECURITY_ALERT` are defined but currently unreachable.** `auth-service` has
no Kafka wiring at all — no outbox, never touched since Phase 2 — and publishing login/security
events to Kafka would mean retrofitting `auth-service`, which is not "Notification + Customer
Support" business logic and wasn't done here (CLAUDE.md's working rule 2: implement only the
requested phase). Left defined rather than deleted so the type catalog matches the originally
scoped design (see notification-service's original scaffold README); a future phase that wires
`auth-service` to Kafka would light these up without any change on this side.

## Customer Support (Phase 13, owned by customer-kyc-service)

The other half of this phase — `POST /api/v1/support-requests` and the `OPEN` → `IN_PROGRESS` →
`RESOLVED` staff workflow — lives in
[customer-kyc-service](../customer-kyc-service#customer-support-phase-13), not here. See
docs/database/domain-model.md ("Exact ownership of `support_requests` will be finalized in Phase
13") for why: a support request is fundamentally tied to the customer raising it, and
`customer-kyc-service` already owns the `Customer` entity. Its resolution is this project's first
real producer to `notification.requested` — the generic topic that existed in the catalog
specifically for this purpose since Phase 8, unused until now.

## API

All endpoints under `/api/v1/notifications`. Self-service for customers (their own notifications
only); staff (`OPERATIONS`/`ADMIN`/`AUDITOR`/`COMPLIANCE_OFFICER`) can additionally filter by
`customerId`. There is no `POST` — ingestion is Kafka-only, see above.

| Method & Path | Auth | Description |
|---|---|---|
| `GET /notifications` | self, or staff filtering by `customerId` | Paginated list, optionally filtered by `read` |
| `GET /notifications/{id}` | owner or staff | Detail |
| `PATCH /notifications/{id}/read` | owner only | Mark read |

## Running locally

From the repository root: `docker compose up -d --build notification-service` (brings up
`postgres` and Kafka — waiting for the `kafka-init` topic bootstrap to complete — automatically).
Listens on `localhost:8088` (`NOTIFICATION_SERVICE_PORT` in `.env`). Health:
`GET /actuator/health` (deliberately does not depend on Kafka reachability — a broker outage
should not flip this service unhealthy; the consumer simply resumes from its last committed
offset once Kafka returns).

## Tests

- `NotificationIngestServiceTest` — unit tests (Mockito): a new event saves a notification; an
  already-seen `eventId` is a no-op; a concurrent duplicate insert (unique-constraint race) is
  tolerated, not thrown.
- `NotificationEventListenerTest` — unit tests proving each of the 6 topics' payload is parsed
  correctly and mapped to the right `NotificationType` and a sensible title/body, plus that a
  missing `customerId` or malformed JSON throws `MalformedNotificationEventException` (which the
  container's retry/DLQ policy handles — not caught here).
- `NotificationEventConsumerIntegrationTest` — **the flagship proof for this service's consumer
  side**, against a real Postgres *and* a real Kafka broker (Testcontainers, not mocked, not
  calling the listener directly): a raw `payment.completed` message published to the real topic is
  genuinely consumed by the running `NotificationEventListener` and lands as a durable row; a
  redelivered message with the same `eventId` does not create a second row; a structurally-broken
  message is retried (backoff shortened for the test) and then genuinely observed landing on the
  real `payment.completed.DLQ` topic via a raw consumer.
- `NotificationControllerIntegrationTest` — full-stack test against real Postgres (seeding rows
  directly via the repository — ingestion is covered separately, above; Kafka is deliberately
  pointed at a non-existent address here so this test never accidentally reaches a real broker and
  ingests real historical messages — see the test class's Javadoc). Covers a customer seeing only
  their own notifications, being rejected `403` reading or marking-read someone else's, staff
  filtering by `customerId`, and unauthenticated `401`.

Verified end-to-end against the live eleven-service stack (`postgres` + `redis` + `kafka` +
`auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service` + `fraud-risk-service` + `audit-service` + `notification-service`): a real KYC
approval, a real account approval, and a real resolved support request each produced the expected
`KYC_STATUS_CHANGED`, `ACCOUNT_STATUS_CHANGED`, and `SUPPORT_REQUEST_RESOLVED` notifications for a
brand-new customer; two real payments against the project's one funded account (which has
accumulated heavy fraud/risk history across every prior phase's live testing) both failed
risk assessment rather than succeeding, which genuinely exercised `PAYMENT_FAILED` and — from the
second payment's `BLOCK` decision plus several independent AML signals on the same transaction —
seven distinct `FRAUD_ALERT` notifications; `PAYMENT_SUCCESS` itself was not produced live in this
pass (no unused funded account remained to send a clean payment from) but is covered by
`NotificationEventListenerTest`'s `onPaymentCompleted` case and the identical code path used for
the other five topics. All of it was independently confirmed as real, queryable notification rows
over RBAC-gated HTTP (owner-only read, `403` across customers, staff `customerId` filtering all
verified live), with the matching simulated email/SMS log lines visible in this container's own
logs, and the support-request resolution's `audit.event` confirmed landed in `audit-service` too
— see the root [README.md](../../README.md#getting-started) for the full transcript.
