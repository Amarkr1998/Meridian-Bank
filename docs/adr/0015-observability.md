# ADR-0015: Observability via Micrometer, Prometheus, and Grafana

**Status:** Accepted
**Date:** 2026-09-05

## Problem

Nine backend services plus the gateway are running, but there is no way to see request rates,
error rates, latency, or JVM/connection-pool health across any of them without shelling into a
container and reading logs one at a time. A production-shaped banking platform needs a real
metrics pipeline: something scraping, storing, and visualizing per-service health over time.

## Decision

Every service exposes `/actuator/prometheus` via Micrometer's Prometheus registry
(`micrometer-registry-prometheus`, added to each service's `pom.xml`), tagged with
`application: ${spring.application.name}` so every metric is attributable to its producing
service. A single Prometheus container (`infrastructure/prometheus/prometheus.yml`) scrapes all
ten targets every 15 seconds; a single Grafana container auto-provisions that Prometheus instance
as its datasource and auto-loads one hand-built dashboard
(`infrastructure/grafana/dashboards/meridian-overview.json`) covering service up/down, HTTP
request rate, HTTP 5xx rate, HTTP p95 latency, JVM heap, HikariCP active connections, and JVM live
threads — every panel split per service.

No custom business metrics (payment throughput, fraud alert rate, reconciliation mismatch count,
etc. as first-class named Micrometer counters/gauges) were added. This phase wires up the
standard, free Spring Boot auto-instrumentation — JVM, HTTP, Tomcat, Hikari — which is already
substantial across ten services and is what "Observability + Prometheus + Grafana" means as
infrastructure. Business-specific metrics would need code changes inside each service's business
logic and are a natural, separate follow-on, not a requirement of standing up the pipeline itself.

`/actuator/prometheus` is added to each service's existing `permitAll` list alongside
`/actuator/health`/`/actuator/info` (see each service's `SecurityConfig`) — Prometheus scrapes
without a JWT, and there both was and is no session-based auth mechanism it could plausibly use.

## Alternatives Considered

- **A hosted/SaaS metrics backend (Datadog, New Relic, Grafana Cloud)** — rejected: this project
  runs entirely locally with no paid cloud services (see CLAUDE.md's Infrastructure constraints);
  self-hosted Prometheus + Grafana is the standard local-first choice and matches the stack
  CLAUDE.md already named.
- **Importing a community Grafana dashboard by ID** (e.g. a generic "Spring Boot Statistics"
  dashboard) — rejected: depends on a specific grafana.com dashboard ID and its JSON schema
  staying compatible with the installed Grafana version, an external dependency this project
  doesn't otherwise take on for anything else. A small hand-built dashboard is fully self-contained
  and exactly matches what these ten services actually expose.
- **Custom business metrics from day one** — rejected for this phase: would mean touching
  business logic in most of the nine backend services just to add instrumentation, which is a
  larger and different kind of change than "stand up the observability pipeline." Left as a
  documented, natural follow-on rather than silently expanded scope.
- **A push-based metrics pipeline (StatsD/OpenTelemetry Collector agents)** — rejected: Prometheus's
  pull model needs zero additional infrastructure beyond the two new containers, and every service
  already runs Spring Boot Actuator, which supports Prometheus's scrape format natively via
  Micrometer with one dependency add.

## Trade-offs

Gains: real, queryable time-series metrics across the whole platform with about the smallest
possible footprint (one new dependency per service, two new containers, no code changes to
business logic). Costs: `/actuator/prometheus` is reachable without authentication on every
service's own port — acceptable for a local demo network (the same trust boundary
`/actuator/health` already lives in) but would need network-level restriction (not public
internet-facing, or IP-allowlisted) in any real deployment; metrics are exposition-format text
scraped every 15s, not real-time; and 15 days of retention (`--storage.tsdb.retention.time=15d`)
is a demo-appropriate default, not a production retention policy.

## Consequences

- Every future service added to this project should follow the same pattern: add
  `micrometer-registry-prometheus`, expose `prometheus` in the actuator exposure list, add it to
  the `permitAll` list, tag with `application`, and add a scrape job to `prometheus.yml`.
- Custom business metrics, if added later, should follow Micrometer's standard
  `Counter`/`Gauge`/`Timer` API and reuse the existing `application` tag convention so they appear
  correctly scoped in Grafana without dashboard rework.
- The Grafana dashboard is provisioning-managed (`allowUiUpdates: true` but backed by a file) —
  a manual edit in the Grafana UI persists to its own database until the next
  `updateIntervalSeconds` reload of the source file overwrites it; treat the JSON file, not the
  running UI, as the source of truth for structural dashboard changes.
