# Data Governance

> **Implementation status (Phase 11):** the "Auditability" principle below — "every access or
> change to `RESTRICTED` data by an internal user is itself an audited action" — is real for the
> action set documented in [audit-service/README.md](../../services/audit-service/README.md)'s
> action catalog (KYC decisions, account/beneficiary/payment lifecycle events, and every
> maker-checker decision), not yet for every conceivable `RESTRICTED`-data access in the system.
> Data classification and masking (the other principles below) remain conventions applied per
> service since the phase that introduced the relevant data, not a separately enforced mechanism —
> see [Security Architecture](../security/security-architecture.md).

## Data Classification

| Level | Description | Examples in Meridian Bank |
|---|---|---|
| `PUBLIC` | Safe for unrestricted disclosure | Product marketing copy, tagline |
| `INTERNAL` | Internal operational data, low sensitivity | Service health, non-customer config |
| `CONFIDENTIAL` | Customer-related data requiring access control | Customer profile, transaction history, beneficiary details |
| `RESTRICTED` | Highest sensitivity; strict access + masking + audit | Credentials, KYC document metadata, full account numbers, fraud/AML case detail |

## Principles

- **Data minimization.** Only the data needed for a stated banking function is collected. No real
  identity documents are ever stored — KYC records hold synthetic metadata only.
- **Access control.** Access to `CONFIDENTIAL` and `RESTRICTED` data is governed by RBAC and
  resource-level authorization (see [Security Architecture](../security/security-architecture.md)).
  Internal roles see only what their function requires (e.g. `AUDITOR` has read-only access to audit
  data, not payment execution).
- **Masking.** `RESTRICTED` and sensitive `CONFIDENTIAL` fields (account numbers, etc.) are masked by
  default in UI and API responses — e.g. `Account: ********4589` — and unmasked only where a
  specific, authorized action requires it.
- **Retention.** Audit events and ledger entries are retained indefinitely within the demo dataset
  (append-only, matching real banking retention expectations); operational/session data (OTPs,
  short-lived risk signals) is retained only as long as functionally needed, expiring out of Redis.
- **Auditability.** Every access or change to `RESTRICTED` data by an internal user is itself an
  audited action.
- **Secure handling.** Passwords, OTPs, JWTs, secrets, full account numbers, and private keys are
  never logged, in line with [Security Architecture](../security/security-architecture.md).

## Maker-Checker (Segregation of Duties)

Governance-sensitive operations — high-value transaction approval, account block/unblock, customer
status changes, configuration changes, high-risk fraud decisions — require two distinct people: a
maker who creates the request and a checker who approves it. The maker can never approve their own
request. See [maker-checker-flow.md](../architecture/maker-checker-flow.md).

## Simplified AML Disclaimer

The AML monitoring capability in this project ([aml-flow.md](../architecture/aml-flow.md)) is a
**simplified, educational implementation** intended to demonstrate monitoring, case management, and
escalation patterns. It is **not** certified for regulatory use, does not implement a full
jurisdictional AML/CTF program, and must never be described or marketed as production banking
compliance.

## Configuration Governance

Business thresholds (transaction limits, fraud rule weights, AML thresholds) are configurable data,
not hardcoded constants scattered through the codebase, so they can be reviewed, changed, and
audited (`CONFIGURATION_CHANGED`) independently of a code deployment.

## Planned Implementation Phase

Phase 11 (Audit + Data Governance) formalized the audit half of enforcement (see the
implementation-status note above); classification and masking rules continue to be applied
incrementally by each service starting from the phase that introduces the relevant data.
