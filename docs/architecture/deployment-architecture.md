# Deployment Architecture

## Local Development (Docker Compose)

All infrastructure and services run locally via `docker-compose.yml` at the repository root — no
paid cloud services are required. Populated starting in Phase 1.

```mermaid
flowchart TB
    subgraph "Docker Compose network"
        FE["frontend (Vite dev / static build)"]
        GW["api-gateway"]
        SVCS["auth / kyc / account / payment /<br/>ledger / fraud-risk / notification / audit"]
        PG[("postgres")]
        KAFKA[("kafka + zookeeper/kraft")]
        REDIS[("redis")]
        PROM["prometheus"]
        GRAF["grafana"]
    end

    FE --> GW --> SVCS
    SVCS --> PG
    SVCS --> KAFKA
    SVCS --> REDIS
    PROM -->|scrape /actuator/prometheus| SVCS
    GRAF --> PROM
```

## Kubernetes (Minikube / Kind)

Each service ships a Deployment + Service + ConfigMap (+ Secret for credentials), with readiness and
liveness probes backed by Spring Boot Actuator, and resource requests/limits sized for local
clusters. Horizontal scaling is demonstrated on stateless services (`api-gateway`, `payment-service`
being the primary candidates given their request volume in the demo flows).

```mermaid
flowchart TB
    ING["Ingress"] --> GWSVC["Service: api-gateway"]
    GWSVC --> GWPOD["Deployment: api-gateway (N replicas)"]
    GWPOD --> SVCMESH["Service-to-service via K8s DNS"]
    SVCMESH --> PODS["Deployments: auth / kyc / account / payment /<br/>ledger / fraud-risk / notification / audit"]
    PODS --> CM["ConfigMaps"]
    PODS --> SEC["Secrets"]
    PODS --> PGEXT[("Postgres<br/>(StatefulSet or external)")]
    PODS --> KAFKAEXT[("Kafka<br/>(StatefulSet or external)")]
    PODS --> REDISEXT[("Redis<br/>(Deployment or external)")]
```

## Environments

This project targets a single **local/demo** environment. There is no staging or production
environment — Meridian Bank is a portfolio project and must never be connected to real banking
infrastructure (see [Safety & Realism Rules](../../README.md#safety--realism-rules)).

## Planned Implementation Phase

Phase 1 (Docker Compose infra), Phase 18 (Kubernetes manifests).
