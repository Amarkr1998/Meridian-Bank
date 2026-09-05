# frontend — Meridian Digital Banking & Operations Portal

**Status:** Phase 14 (customer portal) and Phase 15 (Operations & Compliance portal) both
implemented, in this one codebase/build, as originally scoped.

## What this is

A React + TypeScript + Vite single-page app hosting **two** portals against the same build:

- **Meridian Digital Banking** — the customer portal (Phase 14)
- **Meridian Operations & Compliance** — the internal staff portal (Phase 15)

Both talk exclusively to `api-gateway` (built as a prerequisite for Phase 14 — see
[services/api-gateway/README.md](../services/api-gateway/README.md) and
[ADR-0009](../docs/adr/0009-api-gateway.md), "Implementation notes") on `http://localhost:8080`.
No service port is ever called directly from the browser. Which portal a signed-in user lands on
is decided purely by role (`RootRedirect`, `src/auth/RequireRole.tsx`): `CUSTOMER` goes to
`/dashboard`, every staff role (`OPERATIONS`, `COMPLIANCE_OFFICER`, `RISK_ANALYST`, `AUDITOR`,
`ADMIN`) goes to `/ops/dashboard` — and each portal's routes actively redirect the other kind of
user away, rather than just hiding a nav link.

## Routes

**Customer portal:** `/login`, `/register`, `/mfa`, `/onboarding` (KYC submission), `/dashboard`,
`/accounts`, `/accounts/:accountId`, `/beneficiaries`, `/payments`, `/transactions`,
`/statements`, `/support`, `/support/:requestId`, `/notifications`, `/profile`, `/security`.

**Operations & Compliance portal:** `/ops/dashboard`, `/ops/transactions`, `/ops/fraud` (alerts +
rule configuration, tabbed), `/ops/aml`, `/ops/kyc`, `/ops/accounts` (opening-request queue +
lifecycle management, tabbed), `/ops/customers`, `/ops/approvals` (unified, tabbed across all four
gating services), `/ops/reconciliation`, `/ops/audit`, `/ops/support`, `/ops/system-health`.

`/login`, `/register`, and `/mfa` are the only public routes; everything else requires a session
(`RequireAuth`), and every route beyond that requires the *right kind* of session
(`RequireCustomer` / `RequireStaff`).

## Stack

React 19, TypeScript, Vite, React Router, TanStack Query, Axios, React Hook Form + Zod, MUI.

**MUI is pinned to `^6.5.0`**, not the latest `9.x` available in this registry — v9 shipped a
materially different, stricter polymorphic-component typing (`Stack`/`Typography`/`Grid` all
required an explicit `component` prop just to use ordinary shorthand props like `alignItems` or
`fontWeight`, and `Grid`'s `size` prop moved to a different import). Rather than rewrite this
phase's component usage against an unfamiliar, newly-released major, v6 was chosen because its
API is the well-established one this code is written against. `Grid` specifically is imported
from `@mui/material/Grid2` (the `size={{ xs, sm, md }}` API) since v6's default `Grid` export is
still the legacy `xs=/sm=/md=` API.

## Real backend integration, honestly scoped

Every screen calls a real endpoint — nothing here is mocked or fabricated. Scope notes, matching
the actual backend:

- **Statements** is a client-side grouping of real transaction history by calendar month per
  account — there is no PDF/CSV export endpoint anywhere in the system, and the page says so
  rather than implying one exists.
- **Security**'s "change password" flow reuses the existing self-service
  `password-reset/request` + `password-reset/confirm` endpoints (email + token), because
  auth-service has no separate "change password while logged in" endpoint.
- **MFA enable/disable** isn't offered — there's no backend endpoint for it; Security only
  displays the current `mfaEnabled` status from `GET /auth/me`.
- **Ops Approvals** is one screen backed by four entirely independent, non-identical backend
  queues (account/customer-kyc/fraud-risk/payment-service each own a full `/api/v1/approvals`) —
  see `api/opsApprovals.ts` and the gateway's alias routing. `ApprovalRequestResponse` on the
  frontend is a superset union of all four services' slightly different DTOs (`reason` vs.
  `requestedStatus` vs. `payload` vs. `resultTransactionId`), not a fabricated common shape.
  Fraud-risk-service's fraud-rule-update and alert-resolution requests carry their details in
  `payload` (JSON) rather than `reason`; the UI shows whichever field is present.
  Fraud rule "Rules" tab: the update itself is maker-checker gated — submitting always creates a
  pending approval, never applies immediately, even for the submitting user.
- **Ops System Health** calls `/api/v1/ops/system-health`, a real endpoint on the gateway itself
  (not a service) that fans out `GET /actuator/health` to all eight downstream services — the one
  ops screen with no single backend service that could answer it directly. Staff-role-gated by the
  gateway itself, since there's no downstream service to defer that check to.
- OTP/reset-token values are shown inline in demo-mode responses (`devOtp`/`devResetToken`),
  mirroring the same pattern already used throughout this project's backend for local/demo use
  (no real email/SMS channel exists to deliver them to).

## Auth model

`AuthContext` (`src/auth`) holds the access/refresh token pair in `localStorage` (a demo-only
trade-off — see `tokenStorage.ts`'s comment; a real deployment would use an httpOnly cookie). The
axios client (`src/api/client.ts`) attaches `Authorization: Bearer <token>` to every request and,
on a `401`, transparently refreshes once via `POST /auth/refresh` and retries — only forcing a
logout if the refresh itself fails. `RequireRole.tsx` adds the portal split on top:
`isStaffRole(role)` decides both the post-login redirect (`Login.tsx`, `MfaVerify.tsx`) and
`RootRedirect`'s target for `/`.

## Running locally

```bash
cd frontend
npm install
npm run dev
```

Requires `api-gateway` (and the services it routes to) running — from the repository root:
`docker compose up -d --build api-gateway`. Listens on `http://localhost:5173`
(`VITE_API_BASE_URL` in `.env`, defaulting to `http://localhost:8080`).

`npm run build` runs `tsc -b && vite build` — both a full type-check and a production bundle.

## Tests (Phase 17)

`npm test` (Vitest, `pool: 'threads'` — see the comment in `vite.config.ts` for why: this
workstation's non-ASCII home directory path breaks Vitest's default `forks` pool the same way it
breaks the JDK/Mockito inline mock maker documented in `services/api-gateway/README.md`). 30 tests:

- `src/auth/__tests__/tokenStorage.test.ts` — save/load/clear round-trip, corrupt-JSON handling
- `src/auth/__tests__/RequireRole.test.tsx` — every `RequireStaff`/`RequireCustomer` redirect
  branch (staff sees ops content, customer redirected away from it and vice versa, unauthenticated
  sent to login) plus `isStaffRole`
- `src/auth/__tests__/AuthContext.test.tsx` — the full login lifecycle (direct token issuance vs.
  the MFA-required branch, which must NOT store a session until `verifyMfa` succeeds) and
  logout-clears-the-session-even-if-the-server-call-fails
- `src/api/__tests__/client.test.ts` — the security-relevant one, using `axios-mock-adapter` so the
  *real* interceptor code runs against a mocked transport rather than being reimplemented in the
  test: Authorization header attachment, the transparent 401-refresh-and-retry flow, that two
  concurrent 401s share a single in-flight refresh rather than each firing their own, that a
  rejected refresh token clears the session and notifies exactly once, and `ApiError` wrapping of
  the standard error envelope
- `src/components/__tests__/StatusChip.test.tsx`, `Money.test.tsx` — presentation logic

`npm audit`: 0 vulnerabilities.

## Verification performed

- `tsc -b` — zero type errors across the whole app.
- `npm run build` — production bundle builds successfully.
- `npm run dev` — dev server serves the app shell and every source module without a 404.
- **Phase 14** live, end-to-end against the real 12-service stack via `curl` simulating the exact
  request sequence the customer portal's API layer makes: login → MFA verify → `GET /accounts`,
  `/beneficiaries`, `/payments`, `/notifications`, `/auth/me`, `/auth/sessions`,
  `/support-requests` with the issued bearer token — every response shape matched this codebase's
  TypeScript types exactly. A protected call with no token was rejected `401` at the gateway; an
  unrouted path (`/approvals`) returned `404` from the gateway itself. A real payment POST with an
  `Idempotency-Key` header was replayed with the identical key and returned the same transaction
  rather than reprocessing, proving header forwarding works through the gateway against the real
  payment-service, not just against a test stub.
- **Phase 15** live, end-to-end the same way: staff login through the gateway, then real reads
  across every newly-routed service (`fraud-alerts`, `aml-alerts`, `fraud-rules`,
  `reconciliation-records`, `audit-events`) with correct per-endpoint RBAC confirmed (an
  `OPERATIONS` token genuinely `403`'d on `/audit-events`); all four `/api/v1/ops/approvals/*`
  aliases returned real historical approval requests from their respective services, proving the
  gateway's path-rewrite mechanism against genuine backends; `/api/v1/ops/system-health` returned
  real `UP` statuses for all nine downstream services and correctly `403`'d a customer token; and
  a customer token was rejected `403` on a staff-only path, confirming portal isolation.
- **Not verified: actual browser rendering.** This environment has no headless-browser/screenshot
  tool available, so no page was ever visually inspected or clicked through — a client-side
  rendering error (a component crashing on mount, a broken layout, a hook-order bug in a page with
  conditional queries) would not have been caught by any of the checks above. Worth an explicit
  manual pass in a real browser — logging in as both a customer and each staff role — before
  treating this as production-quality, beyond "compiles, builds, and every API call it makes
  matches the real backend."

## Planned Implementation Phase

Phase 14 (customer portal) and Phase 15 (Operations & Compliance portal) — both in this codebase.
