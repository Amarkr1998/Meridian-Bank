# infrastructure/redis

**Status:** Implemented (Phase 1 — instance only; actual usage — idempotency lookups, rate
limiting, OTP storage, short-lived risk data — is added by the services that own each use case,
starting Phase 2).

## What's here

The `redis` service in `docker-compose.yml` runs `redis:7-alpine` with append-only file (AOF)
persistence enabled (`--appendonly yes`) so local demo state survives a container restart, backed by
the `redis_data` volume.

## Local connection

| | |
|---|---|
| Host | `localhost` (from the host) / `redis` (from another container on `meridian-net`) |
| Port | `6379` (override via `REDIS_PORT` in `.env`) |

No password is set for local/demo use. This must never be exposed outside the local Docker network —
see [Security Architecture](../../docs/security/security-architecture.md) and
[ADR-0004](../../docs/adr/0004-redis-for-idempotency-and-rate-limiting.md) for what Redis is (and
is not) used for — it never holds balances or ledger state.

## Re-initializing

`docker compose down -v` removes the `redis_data` volume for a clean slate.
