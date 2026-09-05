# infrastructure/docker

**Status:** Still empty by design, and that's the completed state, not a gap. Each service's
Dockerfile lives with the service (`services/<name>/Dockerfile`) rather than centrally here —
`services/auth-service/Dockerfile` (Phase 2) was the first example: a multi-stage build using the
Maven Wrapper against `eclipse-temurin:21-jdk-alpine`, running on `eclipse-temurin:21-jre-alpine`.
All nine backend services plus `api-gateway` followed the same pattern as they were built, so the
"Docker half" of Phase 18 (Docker + Kubernetes) was already complete before that phase started —
Phase 18's actual new work was the Kubernetes half, in [infrastructure/kubernetes](../kubernetes),
which reuses these same per-service images unchanged. See [CLAUDE.md](../../CLAUDE.md).
