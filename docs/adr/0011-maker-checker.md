# ADR-0011: Maker-Checker (Four-Eyes) Control

**Status:** Accepted
**Date:** 2026-09-04

## Problem

Some operations are sensitive enough that a single authorized individual acting alone is too much
concentrated power for a banking platform to grant: high-value transaction approval, account
block/unblock, customer status changes, configuration changes, high-risk fraud decisions. RBAC alone
answers "is this role allowed to do this at all," not "should one person be able to do this
unilaterally."

## Decision

Implement maker-checker segregation of duties: the initiating user (maker) creates a
`PENDING_APPROVAL` request; a second, different authorized user (checker) must review and approve it
before the action executes. The server enforces `checker.userId != request.createdBy` — this is not
a UI-only restriction. Every decision (approve/reject/return) is written to the audit trail. See
[docs/architecture/maker-checker-flow.md](../architecture/maker-checker-flow.md).

## Alternatives Considered

- **Trust RBAC alone for sensitive operations** — rejected: a single `ADMIN` or `OPERATIONS` account
  being compromised (or simply making a mistake) would have unchecked reach; four-eyes control is a
  standard real-world banking control precisely because RBAC alone doesn't provide it.
- **UI-only enforcement** (hide the approve button if you're the maker) — rejected: authorization
  enforced only in the frontend is not enforcement at all; the same server-side principle as
  [Security Architecture](../security/security-architecture.md) applies here.

## Trade-offs

Gains: a concrete, auditable control against unilateral misuse or error on high-impact operations.
Costs: adds workflow latency (an action isn't immediate) and requires enough distinct staff/roles in
the demo data to realistically exercise maker ≠ checker.

## Consequences

- `approval_requests`/`approval_actions` tables track the workflow (ownership finalized during
  implementation — see [docs/database/domain-model.md](../database/domain-model.md)).
- The operations approval dashboard (`/ops/approvals`) is the primary UI for checkers.
- Implemented in Phase 10, after the sensitive operations it gates already exist from earlier phases.

## Implementation notes (Phase 10)

Two decisions were made during implementation that narrow the scope described above, both
documented rather than left implicit:

- **Single decision round only — APPROVE or REJECT, no "Return for revision."** The state diagram
  in [maker-checker-flow.md](../architecture/maker-checker-flow.md) shows a `Return` branch back to
  the maker; it was not built. Since a request is decided exactly once, a single
  `approval_requests` table per gating service is sufficient — there is no separate
  `approval_actions` history table. Each row's `decidedBy`/`decidedAt`/`decisionNotes` columns are
  that one decision's complete record.
- **`approval_requests` is duplicated per gating service, not centralized.** Matches this project's
  established no-shared-library convention (ADR-0003: one database per service, no cross-service
  foreign keys) — a centralized approvals service would need to either call back into each owning
  service to execute the approved action (extra synchronous coupling for a control that already
  sits close to the action it gates) or duplicate business logic it doesn't own. Each of
  [account-service](../../services/account-service), [customer-kyc-service](../../services/customer-kyc-service),
  [fraud-risk-service](../../services/fraud-risk-service), and [payment-service](../../services/payment-service)
  owns its own `approval_requests` table, `ApprovalService`, and `ApprovalController` — see each
  service's README for what specifically it gates.
