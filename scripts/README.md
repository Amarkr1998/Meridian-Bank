# scripts

**Status:** Implemented for the final demo/readiness scope (Phase 20).

## Available scripts

`Test-DemoReadiness.ps1` checks the frontend, API gateway, Prometheus, and Grafana endpoints and
exits with an error when the local demo stack is not ready.

Run it from the repository root after `docker compose up -d --build`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/Test-DemoReadiness.ps1
```

The Kafka and database bootstrap scripts remain beside the infrastructure they own. The guided
scenario and safe synthetic personas are in `docs/demo/demo-runbook.md`. Demo state is created
through the public APIs using `demo-data/personas.json` instead of opaque database fixtures, so the
real controls remain visible.
