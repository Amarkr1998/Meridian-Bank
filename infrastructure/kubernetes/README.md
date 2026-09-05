# infrastructure/kubernetes

**Status:** Implemented and live-verified (Phase 18). Plain Kubernetes manifests (no Helm, no
Kustomize) targeting a local single-node cluster (Minikube, Kind, or Docker Desktop's built-in
cluster — this manifest set was verified live against the latter) — matching this project's "plain
over abstraction" approach elsewhere (see e.g. `services/api-gateway/README.md`'s "Why not Spring
Cloud Gateway"). Every pod this manifest set creates (8 backend services, `api-gateway` at 2
replicas, Postgres, Kafka, Redis, Prometheus, Grafana, and the `kafka-init` Job) was confirmed
genuinely `Ready`/`Complete` against a real cluster, and
`api-gateway`'s `/actuator/health` was confirmed reachable end-to-end through a real
`kubectl port-forward` — not just applied and assumed working. See "Real bugs found and fixed
during live verification" below for what that live testing actually caught.

## What's here

One file per logical unit, applied in numeric-prefix order so dependencies exist before anything
that references them:

| File | Contents |
|---|---|
| `00-namespace.yaml` | the `meridian-bank` namespace everything else lives in |
| `01-configmap.yaml` | shared non-secret values (DB/Redis host+port, Kafka bootstrap) |
| `02-secret.yaml` | local/demo credentials — identical values to the root `.env.example` |
| `10-postgres.yaml` | StatefulSet + headless Service + PVC + the same per-service-database init script docker-compose mounts |
| `11-redis.yaml` | Deployment + PVC + Service |
| `12-kafka.yaml` | single-node KRaft StatefulSet + headless Service + PVC |
| `13-kafka-init-job.yaml` | one-shot topic-bootstrap Job (same script as docker-compose's `kafka-init`) |
| `20`–`27-*.yaml` | one Deployment + Service per backend service (auth, customer-kyc, account, payment, ledger, fraud-risk, audit, notification) |
| `28-api-gateway.yaml` | Deployment (2 replicas) + Service |
| `30-autoscaling.yaml` | HorizontalPodAutoscalers for `api-gateway` and `payment-service` |
| `40-prometheus.yaml` | ConfigMap (same scrape config as `infrastructure/prometheus/prometheus.yml`) + Deployment + PVC + Service |
| `41-grafana.yaml` | ConfigMaps (same provisioning + dashboard as `infrastructure/grafana/`) + Deployment + PVC + Service |
| `50-ingress.yaml` | the *only* Ingress rule — routes to `api-gateway`, matching ADR-0009's single-entry-point design |

Every backend service's Kubernetes Service is named identically to its docker-compose service name
(`auth-service`, `postgres`, `kafka`, ...), so every `*_BASE_URL`/`*_URL` environment value and
every Prometheus/Grafana config file is **byte-for-byte identical** to its docker-compose
counterpart — no application code or config had to change for Kubernetes. Same images, same
environment variable contract, different orchestrator.

## Prerequisites

- A local cluster: Minikube (`minikube start --cpus=4 --memory=8192`) or Kind
- `kubectl` pointed at that cluster
- The ingress-nginx addon (`minikube addons enable ingress`, or Kind's ingress-nginx manifest) —
  only needed if you want `50-ingress.yaml` to actually route traffic
- The metrics-server addon (`minikube addons enable metrics-server`) — only needed for
  `30-autoscaling.yaml`'s HPAs to compute anything; without it they'll show `<unknown>` for CPU
  utilization but won't break anything else

## Building and loading images

These are locally-built images, not pushed to any registry — build them via the existing
docker-compose build (it already produces exactly the image names these manifests reference:
`meridianbank-<service>:latest`), then load them into the cluster:

```bash
docker compose build   # from the repository root — builds all nine service images

# Minikube:
for svc in auth-service customer-kyc-service account-service payment-service ledger-service \
           fraud-risk-service audit-service notification-service api-gateway; do
  minikube image load "meridianbank-${svc}:latest"
done

# Kind (replace <cluster-name> if you didn't use the default):
for svc in auth-service customer-kyc-service account-service payment-service ledger-service \
           fraud-risk-service audit-service notification-service api-gateway; do
  kind load docker-image "meridianbank-${svc}:latest"
done
```

## Applying

```bash
kubectl apply -f infrastructure/kubernetes/
kubectl -n meridian-bank get pods -w   # wait for everything to reach Running/Ready
```

`kafka-init` is a Job, not a long-running workload — `kubectl -n meridian-bank get jobs` should
show it `Complete` once Kafka is reachable; `kubectl -n meridian-bank logs job/kafka-init` shows
the same topic-bootstrap output docker-compose's `kafka-init` service produces.

## Accessing it

```bash
kubectl -n meridian-bank port-forward svc/api-gateway 8080:8080
# or, with the ingress-nginx addon enabled and meridian.local pointed at the cluster's IP
# in /etc/hosts:
curl http://meridian.local/actuator/health
```

Prometheus and Grafana: `kubectl -n meridian-bank port-forward svc/prometheus 9090:9090` and
`svc/grafana 3000:3000` respectively (neither is on the Ingress — same as docker-compose, where
they're only reachable via their own published host ports, not through the gateway).

## Where this deliberately differs from docker-compose (and why)

- **No `PLAINTEXT_HOST` Kafka listener.** docker-compose exposes one so the developer's own
  machine can reach `localhost:9092` directly; nothing outside the cluster needs to reach Kafka in
  this design, so it's dropped. Use `kubectl port-forward svc/kafka 19092:19092` if you ever need
  host access for debugging.
- **No `depends_on: condition: service_healthy` equivalent for inter-service HTTP calls.**
  Kubernetes has no native primitive for "don't start pod A until pod B's readiness probe passes,"
  and building one (init containers polling every dependency's `/actuator/health`) for all nine
  services would be a lot of YAML for a problem Kubernetes already handles differently: a pod that
  can't reach a dependency yet just keeps failing its own readiness probe (or crash-loops with
  exponential backoff) until that dependency comes up, and receives no traffic in the meantime.
  This is actually more realistic than docker-compose's strict startup ordering — a real cluster's
  pods restart and reschedule independently all the time, and services must already tolerate that
  (which these do, to varying degrees — e.g. `ledger-service`'s balance lookup already degrades
  gracefully on a downstream failure). The one exception: every backend service gets a
  `wait-for-postgres` init container, since Flyway migration at boot requires the database to be
  reachable and would otherwise crash-loop uselessly for no benefit.
- **Prometheus scrapes Services, not individual pods.** With `api-gateway`/`payment-service`
  running 2 replicas, a scrape lands on whichever pod that Service's load-balancing sends it to —
  not a genuinely distinct per-pod series. A real setup would use `kubernetes_sd_configs` (or the
  Prometheus Operator's `ServiceMonitor` CRD) to discover and scrape every pod individually, which
  needs a ClusterRole/ClusterRoleBinding granting Prometheus's service account permission to list
  pods/endpoints — real additional infrastructure this minimal manifest set doesn't take on. See
  the comment in `40-prometheus.yaml`.
- **Resource requests/limits are demo-sized**, not load-tested — see each Deployment's `resources`
  block. Reasonable local-cluster defaults, not a capacity-planning exercise.

## Real bugs found and fixed during live verification

This manifest set wasn't just reviewed statically — it was applied to a real, live Kubernetes
cluster (Docker Desktop's built-in single-node cluster, 4 CPU / ~8Gi memory) and iterated on until
every pod was genuinely healthy. That process surfaced four real bugs, none visible from reading
the YAML alone:

1. **Exec-probe timeouts on a subprocess-spawning check.** Kafka's original readiness/liveness
   probe (and Postgres's) ran `kafka-broker-api-versions.sh` / `pg_isready` via `exec`, which
   launches a whole new process per check. Kubernetes' exec-probe default `timeoutSeconds` is 1 —
   nowhere near enough for that spawn under real CPU contention. The probe "failing" purely from a
   timeout artifact (not an actual health problem) caused kubelet to kill and restart a perfectly
   healthy Kafka broker — and, more seriously, the actual Postgres database — repeatedly. Fixed by
   giving every exec probe an explicit, generous `timeoutSeconds` (see `10-postgres.yaml` and
   `12-kafka.yaml`).
2. **CPU/memory `requests` sized above the node's real capacity.** The original per-service
   `requests.cpu` values (250m for backend services, 500m for Kafka, etc.) summed to more than this
   node's actual 4 CPU once all Deployments/StatefulSets were applied together, so the scheduler
   correctly refused to place several pods (`FailedScheduling: Insufficient cpu`). Fixed by
   reducing every service's `requests.cpu` to a value that fits real capacity with headroom (limits
   were left generous — they don't gate scheduling).
3. **Liveness probes pointed at the same dependency-aggregated `/actuator/health` used for
   readiness.** Spring Boot's default `/actuator/health` reflects the status of *every* health
   indicator, including downstream dependencies (DB, Kafka) — correct behavior for readiness (stop
   routing traffic here if a dependency is down), but wrong for liveness: a transient Kafka blip
   made every dependent service's liveness probe fail too, so kubelet killed otherwise-perfectly-
   healthy JVMs for a problem restarting them could never fix. Mitigated by widening each
   `livenessProbe`'s `failureThreshold` (giving a downstream blip time to clear before it's treated
   as fatal) rather than a full liveness/readiness health-group split, which would require a Spring
   Security config change and image rebuild across all eight services — noted here as the more
   architecturally correct follow-up, not done in this pass.
4. **A genuine deadlock in Kafka's headless Service.** This single combined broker+controller node
   must resolve its own per-pod DNS name (`kafka-0.kafka`, from `KAFKA_CONTROLLER_QUORUM_VOTERS`)
   to register with its own controller quorum *during startup* — but a headless Service only
   publishes a pod's DNS record once that pod is **Ready** by default, and the pod can never become
   Ready without first completing that registration. The two conditions permanently deadlocked
   (`UnknownHostException: kafka-0.kafka`, 100% reproducible, independent of node load — this was
   never a resource-contention symptom). Fixed with `publishNotReadyAddresses: true` on the `kafka`
   Service — the standard, documented pattern for self-referential StatefulSet members (the same
   fix Cassandra/etcd-style clustered Services need).

None of these four are visible from a static read of the manifests; all four were only found by
watching real `kubectl describe pod` events and container logs against a live cluster. The
equivalent exec-probe-timeout bug (finding #1) was independently confirmed to affect
docker-compose's own `kafka` healthcheck too under heavy host load — see the `healthcheck.timeout`
comment on the `kafka` service in the root `docker-compose.yml`.

**Also observed, not a manifest bug:** running this full K8s deployment *and* the full
docker-compose stack simultaneously on the same machine oversubscribes a single 4-CPU host badly
enough to cause slow JVM boots and intermittent pod restarts in whichever stack has less headroom
at a given moment (self-recovering, not deadlocking, thanks to the fixes above) — an inherent
hardware limitation of running two full copies of a nine-JVM-service system on one small machine,
not something either manifest set can fix. Verify one deployment target at a time for a clean run.

## Teardown

```bash
kubectl delete namespace meridian-bank
```

Deletes everything in this manifest set, including the PersistentVolumeClaims (and therefore all
data) — there is no `-v`-style opt-in/opt-out the way `docker compose down -v` has; deleting the
namespace deletes everything in it.

## Planned Implementation Phase

Phase 18 (Docker + Kubernetes) — the Docker half (per-service Dockerfiles) was already complete as
each service was built; this directory is specifically the Kubernetes half.
