# Meridian Bank Demo Runbook

**Implementation status (Phase 20): complete.** Use synthetic data only.

## Prepare

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
cd frontend && npm ci && npm run dev
```

Wait until every long-running container is healthy and `kafka-init` has completed. Open the portal
at `http://localhost:5173`; Prometheus is at `http://localhost:9090` and Grafana at
`http://localhost:3000`.

For a repeatable preflight on Windows, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/Test-DemoReadiness.ps1
```

## Suggested 12-minute story

1. **Architecture (1 minute):** show the system diagram and explain gateway, service-owned data,
   synchronous decisions, and asynchronous Kafka side effects.
2. **Customer onboarding (2 minutes):** register a synthetic customer, use the demo OTP, submit
   KYC, and show the compliance review queue.
3. **Account and payment (3 minutes):** approve the KYC and account request, verify a beneficiary,
   then submit a payment twice with the same idempotency key and show that only one transaction is
   created.
4. **Controls (2 minutes):** show a fraud/AML alert and the maker-checker rule that prevents the
   initiating staff member from approving their own sensitive action.
5. **Financial integrity (2 minutes):** show the balanced debit/credit ledger entries and explain
   fixed lock ordering and derived balances.
6. **Operations (2 minutes):** show reconciliation, append-only audit events, notifications,
   system health, and the Grafana dashboard.

## Demo identities

The bootstrap administrator is configured by `MERIDIAN_ADMIN_EMAIL` and
`MERIDIAN_ADMIN_PASSWORD`; `.env.example` contains local-only defaults. Create separate synthetic
staff identities for `OPERATIONS`, `COMPLIANCE_OFFICER`, `RISK_ANALYST`, and `AUDITOR` from the
ADMIN-only user endpoint or the documented API. Use two different staff users when demonstrating
maker-checker approval.

The reusable payload source is
[`scripts/demo-data/personas.json`](../../scripts/demo-data/personas.json). It includes five staff
personas, two customer registration profiles, synthetic KYC document metadata, and a clearly
local-only shared password.

Recommended fictional customers:

| Persona | Email | Purpose |
|---|---|---|
| Maya Chen | `maya.chen@example.test` | Source account and customer journey |
| Noah Williams | `noah.williams@example.test` | Destination account and beneficiary |

Use the reserved `.test` domain and never real identity, KYC, account, or payment data.

## Proof points to call out

- JWTs are checked at the gateway and independently at each owning service.
- Resource ownership and RBAC are server-side controls; route guards are only UX.
- Payment retries are safe because the idempotency key and payload fingerprint are enforced.
- Money movement is a balanced, append-only double-entry posting.
- Transactional outboxes keep domain changes and event intent atomic.
- Fraud decisions block the payment path; AML alerts are an independent compliance signal.
- Sensitive operations require a different checker.
- Reconciliation records discrepancies without modifying ledger history.

## Honest limitations

- This is a local educational platform, not a regulated or production-certified bank.
- Email and SMS are simulated; local mode may return OTP/reset values in responses.
- There is no real funding rail, refund/reversal workflow, card network, or external KYC provider.
- A REVIEW payment is retried as a new transaction after approval, not resumed from a durable hold.
- TLS and a real secrets manager are deployment-environment responsibilities.
