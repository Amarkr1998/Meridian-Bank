# infrastructure/prometheus

**Status:** Implemented (Phase 16).

`prometheus.yml` is a static scrape configuration — one job per service, each pointing at
`http://<compose-service-name>:<internal-port>/actuator/prometheus`. Every service already
exposes that endpoint via `micrometer-registry-prometheus` (added to each service's `pom.xml`)
plus `management.endpoints.web.exposure.include: health,info,prometheus` and
`management.metrics.tags.application: ${spring.application.name}` (so every metric is labeled
with which service produced it — see each service's `application.yml`) — this file is purely
"where do I look," no per-service code changes needed beyond that.

No custom business metrics (payment counts, fraud alert rates, etc.) were added — this phase
wires up the standard Spring Boot/Micrometer auto-instrumentation (JVM, HTTP request
latency/rate/status, Hikari connection pool, Tomcat) across every service, which is already
substantial and is what "Observability + Prometheus + Grafana" actually asks for. Custom
first-class business metrics would be a natural, separate follow-on.

## Running locally

Brought up as part of the full stack: `docker compose up -d --build`. Listens on
`http://localhost:9090` (`PROMETHEUS_PORT` in `.env`). Check `http://localhost:9090/targets` to
confirm every job is `UP` — a `DOWN` target usually means that service hasn't finished starting
yet (Prometheus retries every 15s) or its container isn't healthy.

## Planned Implementation Phase

Phase 16 (Observability + Prometheus + Grafana).
