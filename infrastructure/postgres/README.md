# infrastructure/postgres

**Status:** Implemented (Phase 1 — local bootstrap only; Flyway migrations are added per-service
starting in Phase 3).

## What's here

`init-databases.sh` runs once, automatically, via Postgres's `/docker-entrypoint-initdb.d`
mechanism the first time the `postgres` container starts against an empty data volume. It creates
one **database** per service (true isolation, not just a schema within a shared database — see
[ADR-0003](../../docs/adr/0003-postgresql-system-of-record.md)):

```text
auth_service
customer_kyc_service
account_service
payment_service
ledger_service
fraud_risk_service
notification_service
audit_service
```

`api-gateway` and `fraud-risk-service`'s AML monitoring share no database of their own beyond what's
listed above — `fraud_rules`/`fraud_alerts`/`aml_alerts` all live in `fraud_risk_service`, and
`api-gateway` is stateless (see [docs/architecture/system-architecture.md](../../docs/architecture/system-architecture.md)).

## Local connection

With the stack running (`docker compose up -d` from the repo root):

| | |
|---|---|
| Host | `localhost` (from the host) / `postgres` (from another container on `meridian-net`) |
| Port | `5432` (override via `POSTGRES_PORT` in `.env`) |
| User / Password | `POSTGRES_USER` / `POSTGRES_PASSWORD` in `.env` (defaults: `meridian` / `meridian_local_dev` — local demo credentials only, never real secrets) |
| Databases | one per service, listed above |

Each service's future Flyway migrations (Phase 3 onward) run against its own database only, using
that service's own datasource configuration — no service is ever configured to reach another
service's database.

## Re-initializing

The init script only runs against a fresh volume. To reset local data:
`docker compose down -v` (removes the `postgres_data` volume) then `docker compose up -d`.
