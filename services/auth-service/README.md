# auth-service

**Status:** Implemented (Phase 2 — Auth/IAM + JWT + RBAC + MFA).

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL (`auth_service` database),
Redis, Flyway. See [pom.xml](pom.xml) for the full dependency set.

## Responsibility

Identity and access management for both customers and internal staff — see
[docs/security/security-architecture.md](../../docs/security/security-architecture.md) and
[docs/adr/0010-rbac.md](../../docs/adr/0010-rbac.md).

- Self-service customer registration (`CUSTOMER` role only)
- ADMIN-only staff provisioning (`OPERATIONS`, `COMPLIANCE_OFFICER`, `RISK_ANALYST`, `AUDITOR`, `ADMIN`)
- Login with password + MFA (OTP) two-step flow
- JWT access tokens (short-lived, HS256) + opaque refresh tokens (rotated on every use, hashed at rest)
- Account lockout after repeated failed logins; every login attempt is recorded
- Forgot-password flow (request/confirm) with all active sessions revoked on password change
- Session listing/revocation (`GET/DELETE /api/v1/auth/sessions`)
- RBAC enforced with `@PreAuthorize` at the method level, plus resource-level checks (a
  customer's session listing/revocation is scoped to that customer's own tokens)

## API

All endpoints under `/api/v1/auth`. Standard error envelope on failure — see
[docs/api/api-governance.md](../../docs/api/api-governance.md).

| Method & Path | Auth | Description |
|---|---|---|
| `POST /register` | public | Self-service customer registration |
| `POST /login` | public | Step 1: password check → MFA challenge (or tokens if MFA disabled) |
| `POST /mfa/verify` | public | Step 2: OTP check → tokens |
| `POST /refresh` | public | Rotate a refresh token for a new access/refresh pair |
| `POST /logout` | public | Revoke one refresh token |
| `POST /password-reset/request` | public | Always 202; issues a reset token if the email matches |
| `POST /password-reset/confirm` | public | Set a new password, revoking all sessions |
| `GET /me` | authenticated | Caller's own identity |
| `GET /sessions` / `DELETE /sessions/{id}` / `DELETE /sessions` | authenticated | Session management |
| `POST /users` | `ADMIN` | Provision a staff identity with an explicit role |
| `GET /users` | `ADMIN` | Paginated user listing |

## MFA / password-reset demo-mode note

There is no real email/SMS channel until `notification-service` (Phase 13). In local/demo mode
(`meridian.security.mfa.demo-expose-otp` / `...password-reset.demo-expose-token`, both default
`true`), the OTP and password-reset token are returned directly in the API response instead of
only being "sent" — standing in for that missing channel so the flows are testable end-to-end.
This is a deliberate, documented simplification for a fictional demo bank; it must never be
enabled in a real deployment. Neither value is ever written to logs (see
[docs/governance/governance-principles.md](../../docs/governance/governance-principles.md)).

## Bootstrap admin

`AdminBootstrapRunner` seeds one `ADMIN` user on first startup (skipped if an `ADMIN` already
exists) from `MERIDIAN_ADMIN_EMAIL` / `MERIDIAN_ADMIN_PASSWORD` — local/demo defaults are in
`.env.example`. This is the only way to reach the `ADMIN`-only `/users` provisioning endpoint on a
fresh environment.

## Running locally

From the repository root: `docker compose up -d --build auth-service` (brings up its `postgres`
and `redis` dependencies automatically). Listens on `localhost:8081`
(`AUTH_SERVICE_PORT` in `.env`). Health: `GET /actuator/health`.

To run/test outside Docker: `./mvnw spring-boot:run` (needs `DB_HOST`/`REDIS_HOST` pointing at a
reachable Postgres/Redis — defaults assume `localhost`). `./mvnw test` runs the full suite,
including a Testcontainers-backed integration test that needs a working Docker daemon.

## Tests

- `JwtServiceTest`, `AuthenticationServiceTest` — unit tests (Mockito) covering token
  issuance/expiry, invalid login, and account lockout.
- `AuthControllerIntegrationTest` — full-stack test against real Postgres + Redis
  (Testcontainers): register → login → MFA → tokens → `/me`, refresh rotation, logout, RBAC
  allow/deny on the admin-only endpoint, and unauthenticated access.

## Cross-service integration

`customer-kyc-service` (Phase 3) calls `POST /api/v1/auth/register` synchronously to provision a
customer's login identity at registration time, then owns the profile/KYC data under the same id
— see [services/customer-kyc-service](../customer-kyc-service). Every other service that needs to
authorize a request verifies the JWT independently using the same signing secret, rather than
calling back into this service or trusting a gateway — see
[docs/security/security-architecture.md](../../docs/security/security-architecture.md).

## Not in scope for this phase

No Kafka involvement: `auth-service` is not a producer or consumer of any topic in
[docs/kafka/topics.md](../../docs/kafka/topics.md) — it's a pure synchronous identity/JWT service.
`customer-kyc-service`, `account-service`, and `payment-service` publish real events via the
transactional outbox pattern as of Phase 8 — see their READMEs.
