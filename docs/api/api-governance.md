# API Governance

## Versioning

All endpoints are namespaced under `/api/v1/...`. Breaking changes are introduced as `/api/v2/...`
rather than mutating `v1` contracts in place; additive/backward-compatible changes may land in `v1`.

## Documentation

Every service exposes an OpenAPI/Swagger spec generated from its controllers/DTOs. The spec is the
authoritative contract for that service's API — request/response shapes are documented there rather
than duplicated in prose docs.

## Conventions

- **HTTP status codes** used per their standard semantics (`200`/`201` success, `400` validation,
  `401` unauthenticated, `403` unauthorized, `404` not found, `409` conflict — e.g. duplicate
  idempotency key with differing payload, `422` business rule violation, `500` unexpected error).
- **Pagination** on all list endpoints (`page`, `size`, plus total count in the response envelope).
- **Filtering & sorting** via query parameters on list endpoints (e.g. `status`, `dateFrom`,
  `dateTo`, `sort`).
- **Correlation IDs** — every request is assigned or propagates an `X-Correlation-Id`, threaded
  through logs, audit events, and Kafka event envelopes for end-to-end traceability.
- **Idempotency** — mutating financial endpoints require an `Idempotency-Key` header (see
  [ADR-0006](../adr/0006-idempotency.md)).

## Error Format

All error responses share one envelope:

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "Transaction could not be processed",
  "correlationId": "abc-123"
}
```

- `code` is a stable, machine-readable identifier the frontend can branch on.
- `message` is a safe, user-presentable description — never an internal exception message or stack
  trace.
- `correlationId` lets support/operations staff locate the request in logs and audit trail.

## Security

Every endpoint requires authentication except the public login/registration/health endpoints.
Authorization (RBAC + resource-level) is enforced in the owning service, not just at the gateway —
see [Security Architecture](../security/security-architecture.md).

## Planned Implementation Phase

Conventions apply from the first service onward (Phase 2). This document is updated as patterns are
established, not retrofitted at the end.
