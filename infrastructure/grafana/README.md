# infrastructure/grafana

**Status:** Implemented (Phase 16).

- `provisioning/datasources/datasource.yml` — auto-registers Prometheus (`http://prometheus:9090`)
  as Grafana's default datasource on container startup. No manual "Add data source" click-through.
- `provisioning/dashboards/dashboard.yml` — tells Grafana to auto-load every dashboard JSON found
  under `/var/lib/grafana/dashboards` (mounted from `dashboards/` in this directory) into a
  "Meridian Bank" folder.
- `dashboards/meridian-overview.json` — one hand-built dashboard, not an imported community one
  (avoids depending on a specific grafana.com dashboard ID staying available/compatible). Panels:
  service up/down (all nine backend services + the gateway), HTTP request rate, HTTP 5xx error
  rate, HTTP p95 latency, JVM heap used, HikariCP active connections, and JVM live thread count —
  every panel split **per service** (`application` label) on one shared graph, so a regression in
  one service is visible against the others' baseline.

## Running locally

Brought up as part of the full stack: `docker compose up -d --build`. Listens on
`http://localhost:3000` (`GRAFANA_PORT` in `.env`). Sign in with `GRAFANA_ADMIN_USER`/
`GRAFANA_ADMIN_PASSWORD` (`.env` — local demo defaults only, same pattern as every other seeded
credential in this project). The "Meridian Bank" folder's "Meridian Bank — Service Overview"
dashboard is there immediately, no setup required.

## Planned Implementation Phase

Phase 16 (Observability + Prometheus + Grafana).
