# Security Architecture

> **Implementation status (Phase 17):** a dedicated security-testing pass — see
> [docs/testing/testing-strategy.md](../testing/testing-strategy.md) for the full detail. Found
> and fixed two real gaps: four of nine backend services
> (`customer-kyc-service`/`account-service`/`payment-service`/`fraud-risk-service`) had no test
> proving their auth boundary rejects a missing/garbled/expired token — every existing test assumed
> a request already carried *some* valid token; and `api-gateway`, having no Spring Security at
> all, shipped its own directly-generated error/health responses without the baseline security
> headers every other service gets for free. Both are fixed and now regression-tested. Also
> audited (and found clean): no raw/native/string-concatenated SQL anywhere in the codebase, and no
> log statement anywhere leaks a password/token/secret/OTP/account number. Dependency freshness was
> checked (all services already on the latest stable Spring Boot line); a full CVE-level dependency
> scan (OWASP Dependency-Check) was attempted and found infeasible in this environment without an
> NVD API key — documented honestly as a real, acknowledged gap rather than silently skipped.

## Authentication

- Credential-based login (Customer ID/email + password) for customers; internal staff authenticate
  the same way against `auth-service` with their assigned role.
- Passwords are hashed (never stored or logged in plaintext).
- JWT access tokens (short-lived) + refresh tokens (longer-lived, rotated on use) issued by
  `auth-service`. The API gateway validates the access token on every request and propagates
  identity/role claims to downstream services.
- Account lockout after a configurable number of failed login attempts; failed attempts are tracked
  and audited.
- MFA/OTP simulation as a second factor at login and before high-risk actions (e.g. adding a new
  beneficiary, high-value transfers). Implemented in `auth-service` (Phase 2): until
  `notification-service` exists (Phase 13) there is no real delivery channel, so in local/demo
  mode the OTP (and, for the analogous password-reset flow, the reset token) is returned directly
  in the API response rather than only logged — this is a deliberate, documented simplification,
  gated behind config that defaults on for local/demo only and must never be enabled in a real
  deployment. Neither value is ever written to logs even in demo mode. See
  [services/auth-service/README.md](../../services/auth-service/README.md).
- Session management: session/token expiration, explicit logout invalidation, and a "active
  sessions" view in the customer Security Center (backend session listing/revocation implemented
  in Phase 2; the Security Center UI itself lands with the customer portal in Phase 14).

## Authorization

- **RBAC** with six roles: `CUSTOMER`, `OPERATIONS`, `COMPLIANCE_OFFICER`, `RISK_ANALYST`,
  `AUDITOR`, `ADMIN`. Each internal role maps to a bounded set of permissions on specific
  resources/actions (e.g. only `COMPLIANCE_OFFICER` can approve/reject KYC; only `RISK_ANALYST` and
  above act on fraud alerts).
- **Resource-level authorization** is enforced in every service that owns customer data — a
  `CUSTOMER` token is checked against the `customerId`/`accountId` on every request, not just at the
  gateway. Changing an ID in a request must never grant access to another customer's data.
- **The backend is the final authorization boundary.** Frontend route guards and role-based
  navigation exist for UX only; every enforcement decision is re-checked server-side.
- **Maker-checker** adds a second authorization dimension on top of RBAC for sensitive operations —
  see [maker-checker-flow.md](../architecture/maker-checker-flow.md). The checker's identity is
  compared against the maker's at approval time, server-side.

## Data Protection

- Sensitive values are masked in API responses, UI, and logs (e.g. `Account: ********4589`).
- Passwords, OTPs, JWTs, secrets, full account numbers, and private keys are **never** logged.
- Data classification (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`) drives access control and
  masking rules — see [Governance Principles](../governance/governance-principles.md).
- Secrets (DB credentials, JWT signing keys, etc.) are never hardcoded; local development uses
  environment variables / Docker secrets, never committed to the repository (see `.gitignore`).

## Resilience & Abuse Prevention

- Rate limiting at the gateway (Redis-backed) to blunt brute-force login and API abuse.
- Resilience4j timeout/retry/circuit-breaker/bulkhead policies on inter-service calls — **financial
  transactions are never blindly retried**; retries are only safe because of the `Idempotency-Key`
  mechanism (see [ADR-0006](../adr/0006-idempotency.md)).
- Secure headers and CORS policy enforced at the gateway.

## Input Validation & Error Handling

- Bean Validation on all request DTOs; validation failures return a structured error, never a stack
  trace.
- Consistent API error envelope (`code`, `message`, `correlationId`) — see
  [API Governance](../api/api-governance.md). Internal exception details are never exposed to
  clients.

## Auditability

Sensitive actions (registration, KYC decisions, account/beneficiary events, payments, fraud/AML
alert creation, and every maker-checker decision) are written to a real, append-only audit trail
with actor, role, action, resource, timestamp, result, and correlation ID, consumed and stored by
`audit-service` (Phase 11) — not every conceivable sensitive action is covered yet; see
[audit-service](../../services/audit-service/README.md) for the exact action catalog.

## Transport Security

Local/demo deployment runs over plain HTTP for simplicity; the Kubernetes deployment documentation
([deployment-architecture.md](../architecture/deployment-architecture.md)) describes where TLS
termination (ingress-level HTTPS) would sit in a real deployment, without provisioning real
certificates for this portfolio project.

## Planned Implementation Phase

Phase 2 (Auth/IAM + JWT + RBAC + MFA) for authentication and RBAC; authorization checks are then
implemented incrementally alongside each domain phase that introduces protected resources.
