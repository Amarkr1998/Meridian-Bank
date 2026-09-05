# fraud-risk-service

**Status:** Implemented (Phase 9 — Fraud + Risk + AML; Phase 10 gated fraud alert resolution and
fraud rule updates behind maker-checker approval; Phase 11 wired real audit-trail publishing in).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`fraud_risk_service`
database), Kafka, Flyway. See [pom.xml](pom.xml). No Redis, no Resilience4j — this service makes
no outbound synchronous calls to any other Meridian service; it's called, not calling.

> **This is a simplified, educational fraud/AML implementation.** It demonstrates rule-based fraud
> scoring and AML monitoring/case-management patterns. It is **not** certified for regulatory use,
> does not implement a full jurisdictional AML/CTF program, and must never be described or
> marketed as production banking compliance — see
> [docs/governance/governance-principles.md](../../docs/governance/governance-principles.md).

## Responsibility

Real-time fraud risk scoring for payments, plus simplified AML signal monitoring — see
[docs/architecture/fraud-flow.md](../../docs/architecture/fraud-flow.md) and
[aml-flow.md](../../docs/architecture/aml-flow.md).

- `POST /api/v1/risk-assessments` — called synchronously by [payment-service](../payment-service)
  during its `RISK_CHECK` step, forwarding the paying customer's own bearer token. Runs the
  configured FRAUD-category rules (weighted scoring, 0-100) and AML-category signals against the
  transaction and this service's own assessment history, in one call. Returns
  `ALLOW`/`REVIEW`/`BLOCK`, which payment-service enforces — this service makes the call, it
  doesn't move money.
- Fraud alert queue (`fraud_alerts`) — one created per REVIEW/BLOCK decision, with a
  `OPEN → UNDER_REVIEW → {CLEARED, ESCALATED, CONFIRMED_FRAUD}` lifecycle.
- AML alert queue (`aml_alerts`) — one created per triggered AML signal, independent of the fraud
  decision, `OPEN → UNDER_REVIEW → {CLEARED, ESCALATED}`.
- Configurable rule catalog (`fraud_rules`) — weights/thresholds are business data, editable by
  staff without a code deployment — see
  [Governance Principles](../../docs/governance/governance-principles.md) ("Configuration
  Governance").

## Trust boundary

Like [ledger-service](../ledger-service), `POST /risk-assessments` performs **no ownership or
business-authorization checks of its own** — it trusts the caller (currently only
`payment-service`) to have already validated that an assessment should happen. It only verifies
the caller holds a valid JWT (see `SecurityConfig`). This is deliberate: by the time a payment
reaches `RISK_CHECK`, `payment-service` has already run its own validation pipeline. This is
explicitly *not* the final security boundary for payments — `payment-service` is (see its README
for the full precedent: every synchronous call it makes, including this one, fails the whole
request if the callee is unreachable, rather than silently allowing or blocking).

## Fraud rules (default set, all tunable via `PATCH /api/v1/fraud-rules/{id}`)

Scoring bands: 0-30 `LOW`→`ALLOW`, 31-70 `MEDIUM`→`REVIEW`, 71-100 `HIGH`→`BLOCK` (score capped at
100). Each enabled rule that triggers adds its weight; the total determines the band.

| Rule | Default weight | Default trigger |
|---|---|---|
| `HIGH_AMOUNT` | 40 | Amount ≥ $5,000 |
| `HIGH_VELOCITY` | 30 | ≥ 5 assessments for this customer in the trailing 1h |
| `NEW_BENEFICIARY_HIGH_AMOUNT` | 25 | First-ever transfer to this destination account, amount ≥ $1,000 |
| `REPEAT_RISKY_BEHAVIOR` | 35 | ≥ 2 REVIEW/BLOCK decisions for this customer in the trailing 30d |
| `RAPID_SEQUENTIAL_TRANSFERS` | 45 | ≥ 3 assessments for this customer in the trailing 60s |

"This customer's history" means this service's own `risk_assessments` table — an append-only
record of every assessment ever made, which is the *only* source of transaction history available
here (see `RiskAssessment`'s Javadoc). There is no Kafka consumer feeding this from
`payment-service`; every row comes from a real `POST /risk-assessments` call.

## AML signals (default set, same rule table, `category = AML`, don't score)

| Signal | Default trigger |
|---|---|
| `HIGH_VALUE` | Amount ≥ $10,000 |
| `VELOCITY` | Rolling 24h transaction volume for this customer ≥ $20,000 |
| `STRUCTURING` | ≥ 3 transactions in [$8,000, $10,000) for this customer within 24h |
| `REPEATED_NEW_BENEFICIARY` | ≥ 3 transfers to the same destination account within 24h |

AML signals are independent of the fraud decision — see aml-flow.md. A payment can be `ALLOW`ed by
the fraud engine while still raising an AML alert for the compliance queue; the AML alert never
blocks the payment itself (see [Governance Principles](../../docs/governance/governance-principles.md),
"Simplified AML Disclaimer" — this is intentionally *not* a blocking control).

## Kafka events (Phase 8 pattern)

Publishes `fraud.detected` for every fraud alert created and every status change, and for every
AML alert created and every status change, via the transactional outbox pattern — see
[docs/adr/0005-outbox-pattern.md](../../docs/adr/0005-outbox-pattern.md). Same `OutboxWriter`/
`OutboxPublisher` implementation as the three Phase 8 services (duplicated, not shared). As of
Phase 13, [notification-service](../notification-service) genuinely consumes `fraud.detected`
(`FRAUD_ALERT` notifications, one per alert/status-change message — not deduplicated by alert, so
a customer sees one notification per event, same as every other consumed topic here).

## What's honestly still a placeholder

- **KYC-review risk lookup isn't wired into `customer-kyc-service`.**
  [docs/architecture/kyc-flow.md](../../docs/architecture/kyc-flow.md)'s sequence diagram shows the
  *Operations Portal* — not `customer-kyc-service` itself — calling for a risk summary during KYC
  review. The Operations Portal doesn't exist until Phase 15, so there's no real caller for this
  today; `GET /api/v1/customers/{id}/risk-summary` exists so that future caller has something real
  to call, but nothing invokes it yet.
- **REVIEW still fails the payment rather than placing it on a true hold.** A REVIEW-flagged
  payment still comes back `FAILED`/`FRAUD_REVIEW_REQUIRED` from this service's decision — that
  hasn't changed. What Phase 10 adds is a maker-checker-gated *release* path in
  [payment-service](../payment-service) (`POST /payments/{id}/request-release` →
  `PATCH /api/v1/approvals/{id}/approve`) that lets staff create a brand-new transaction re-attempting
  the transfer after review, bypassing `RISK_CHECK`. It is not a resume of the original transaction
  (a real hold/resume primitive) — see payment-service's README for exactly what it does and
  doesn't do.
- **Single-instance assumption** for the outbox publisher (see `OutboxPublisher`'s Javadoc) — same
  documented simplification as every other Phase 8 service.

## Audit trail (Phase 11)

Publishes `audit.event` (same duplicated `audit` package pattern as every other producing service
— see [docs/adr/0012-audit-architecture.md](../../docs/adr/0012-audit-architecture.md)) for
`FRAUD_ALERT_CREATED` and `AML_ALERT_CREATED` (both fired from `RiskAssessmentService.assess`,
alongside the existing `fraud.detected` events — with `actorId` left `null`, since a real-time risk
assessment has no human actor, only a subject customer named in `detail`), plus
`APPROVAL_CREATED`/`APPROVAL_COMPLETED` for both gated actions, plus a distinct
`CONFIGURATION_CHANGED` event on the fraud rule's own `resourceId` specifically when an approved
rule update actually applies (see
[Governance Principles](../../docs/governance/governance-principles.md), "Business thresholds ...
audited independently of a code deployment"). Consumed and durably stored by
[audit-service](../audit-service).

## Maker-checker (Phase 10)

Fraud alert resolution (`clear`/`escalate`/`confirm`) and fraud rule updates
(`PATCH /fraud-rules/{id}`) are both gated behind a two-person approval — see
[docs/adr/0011-maker-checker.md](../../docs/adr/0011-maker-checker.md) and
[docs/architecture/maker-checker-flow.md](../../docs/architecture/maker-checker-flow.md). Both
endpoints now return `202 Accepted` with an `ApprovalRequestResponse` instead of acting
immediately: a maker creates a `PENDING_APPROVAL` request, and a *different* staff member must
decide it via `PATCH /api/v1/approvals/{id}/approve` or `.../reject` before the action actually
executes.

Since the two gated actions have genuinely different-shaped payloads (a target
`FraudAlertStatus` + notes vs. a rule's weight/threshold/enabled fields), `ApprovalRequest` stores
the payload as a JSON string (via the injected `ObjectMapper`) rather than typed columns — reusing
the envelope-as-JSON-string precedent already established by this service's own `OutboxWriter`.

The checker role is also action-specific, enforced by a dedicated `ApprovalAuthorization` SpEL bean
(`@approvalAuthorization.canDecide(#id, authentication)`) rather than the controller's static
`@PreAuthorize`: a `FRAUD_RULE_UPDATE` may only be decided by `COMPLIANCE_OFFICER`/`ADMIN` (matching
who can `PATCH /fraud-rules/{id}` today), while a `FRAUD_ALERT_RESOLUTION` may be decided by
`RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`. `ApprovalService.requirePending` separately enforces
that the checker cannot be the same user as the maker regardless of role
(`403 SELF_APPROVAL_NOT_ALLOWED`).

## API

| Method & Path | Auth | Description |
|---|---|---|
| `POST /risk-assessments` | any authenticated caller (trust boundary — see above) | Assess a transaction; returns ALLOW/REVIEW/BLOCK |
| `GET /customers/{id}/risk-summary` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR` | Aggregate risk profile for a customer |
| `GET /fraud-alerts` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR` | Review queue, filterable by `status`/`customerId` |
| `GET /fraud-alerts/{id}` | same as above | Alert detail |
| `PATCH /fraud-alerts/{id}/start-review` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN` | OPEN → UNDER_REVIEW |
| `PATCH /fraud-alerts/{id}/clear` \| `/escalate` \| `/confirm` | same as above | **Maker-checker gated** — returns `202` with an approval request |
| `GET /aml-alerts` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR` | Compliance queue — `RISK_ANALYST` can view only |
| `GET /aml-alerts/{id}` | same as above | Alert detail |
| `PATCH /aml-alerts/{id}/start-review` \| `/clear` \| `/escalate` | `COMPLIANCE_OFFICER`/`ADMIN` only | OPEN → UNDER_REVIEW → CLEARED/ESCALATED |
| `GET /fraud-rules` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR` | List the rule catalog |
| `GET /fraud-rules/{id}` | same as above | Rule detail |
| `PATCH /fraud-rules/{id}` | `COMPLIANCE_OFFICER`/`ADMIN` only | **Maker-checker gated** — returns `202` with an approval request |
| `GET /approvals` | `RISK_ANALYST`/`COMPLIANCE_OFFICER`/`ADMIN`/`AUDITOR` | Review queue, filterable by `status` |
| `GET /approvals/{id}` | same as above | Request detail |
| `PATCH /approvals/{id}/approve` | Action-specific checker role (see above), and ≠ the requesting maker | Approve — actually executes the gated action |
| `PATCH /approvals/{id}/reject` | Action-specific checker role (see above), and ≠ the requesting maker | Reject — no change made |

## Running locally

From the repository root: `docker compose up -d --build fraud-risk-service` (brings up `postgres`
and Kafka — waiting for the `kafka-init` topic bootstrap to complete — automatically). Listens on
`localhost:8086` (`FRAUD_RISK_SERVICE_PORT` in `.env`). Health: `GET /actuator/health`
(deliberately does not depend on Kafka reachability). The default rule set is seeded on first
startup by `FraudRuleBootstrapRunner` (same idempotent pattern as auth-service's
`AdminBootstrapRunner`).

## Tests

- `FraudRuleEngineTest` / `AmlSignalEvaluatorTest` — unit tests (Mockito) proving each rule's
  triggering condition and the score/decision banding in isolation from the database.
- `RiskAssessmentServiceTest` — unit tests proving the orchestration: every assessment is
  recorded; an ALLOW with no AML signals creates no alerts; a REVIEW/BLOCK always creates a fraud
  alert; every triggered AML signal creates its own alert independently of the fraud decision;
  `fraud.detected` is published for each alert created.
- `FraudAlertServiceTest` — unit tests for the alert state machine and its guard conditions.
- `FraudRiskControllerIntegrationTest` — full-stack test against real Postgres (Testcontainers),
  proving the velocity/frequency rules actually accumulate against real data, not a mocked
  repository: a customer's 4th rapid assessment genuinely sees the prior 3 real rows and its score
  changes accordingly. Also covers the full alert lifecycle over real HTTP (now via the
  maker-checker `request → approve` flow, Phase 10), the RBAC difference between fraud alerts and
  AML alerts (`RISK_ANALYST` can view an AML alert but a `PATCH` on it correctly returns `403`),
  and that updating a rule via the maker-checker flow changes the next assessment's outcome. Two
  Phase 10 tests specifically prove the action-specific checker restriction:
  `alertResolution_riskAnalystCannotApproveTheirOwnRequest` and
  `ruleUpdate_riskAnalystCannotApproveEvenAsADifferentPerson` (a `RISK_ANALYST` is a valid alert
  checker but not a valid rule-update checker, even as a genuinely different person from the maker).
- `ApprovalServiceTest` — unit tests (Mockito) for the maker-checker workflow, including
  `approve_byTheMakerThemselves_isRejected` and that `reject` never executes the gated action.

Verified end-to-end against the live nine-service stack (`postgres` + `redis` + `kafka` +
`auth-service` + `customer-kyc-service` + `account-service` + `payment-service` +
`ledger-service` + `fraud-risk-service`): a real customer registered, verified KYC, opened and
funded an account, and added a beneficiary; a $50 payment cleanly `ALLOW`ed; two $9,000+ payments
each scored 40 (`HIGH_AMOUNT`) and came back `REVIEW`, failing with `FRAUD_REVIEW_REQUIRED` and
leaving the account balance untouched; a fourth rapid payment scored 100 (`HIGH_AMOUNT` +
`REPEAT_RISKY_BEHAVIOR` + `RAPID_SEQUENTIAL_TRANSFERS`, capped) and came back `BLOCK`, failing
with `FRAUD_BLOCKED`; the same sequence organically triggered real `REPEATED_NEW_BENEFICIARY` and
`STRUCTURING` AML alerts; every alert was read back from Postgres and from the real
`fraud.detected` Kafka topic (`kafka-console-consumer`); and a provisioned `RISK_ANALYST` staff
account reviewed and confirmed the `BLOCK` alert over real HTTP (`OPEN → UNDER_REVIEW →
CONFIRMED_FRAUD`), while a `CUSTOMER` token attempting the same action was correctly rejected with
`403`. (As of Phase 10, that final `UNDER_REVIEW → CONFIRMED_FRAUD`/`CLEARED`/`ESCALATED` step no
longer executes immediately — see "Maker-checker (Phase 10)" below for what replaced it.)

Phase 10 additionally verified both gated actions live: for alert resolution, a `RISK_ANALYST`
maker's `PATCH /fraud-alerts/{id}/clear` on a real `REVIEW`-decision alert (from a genuine $9,000
new-beneficiary payment, score 65) returned `202 PENDING_APPROVAL` and left the alert
`UNDER_REVIEW`; the maker's own approval attempt was rejected `403 SELF_APPROVAL_NOT_ALLOWED`; a
different `COMPLIANCE_OFFICER` checker's approval returned `200 APPROVED`; and the alert was then
confirmed genuinely `CLEARED`. For rule updates, a `COMPLIANCE_OFFICER` maker's
`PATCH /fraud-rules/{id}` (changing `HIGH_VELOCITY`'s weight 30 → 33) returned `202
PENDING_APPROVAL` and left the weight at 30; the maker's own approval attempt was rejected `403`;
a different `RISK_ANALYST` — a valid alert-resolution checker, but not a valid rule-update
checker — was also correctly rejected `403` by the `ApprovalAuthorization` role check; and a
different `ADMIN`'s approval returned `200 APPROVED`, with the weight then confirmed genuinely 33.
