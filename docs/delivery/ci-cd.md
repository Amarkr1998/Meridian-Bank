# CI/CD and Delivery

**Implementation status (Phase 19): complete.**

Meridian Bank has no hosted production or staging environment. Its delivery pipeline therefore
stops at a verified, traceable local-deployment bundle instead of pretending to deploy to an
unconfigured cloud account.

## Pipeline

`.github/workflows/ci.yml` runs on pull requests, pushes to `main`, manual dispatches, and version
tags matching `v*`.

| Gate | What it proves |
|---|---|
| Backend matrix | All nine Spring services pass `mvn verify`, including Testcontainers tests |
| Frontend | Clean install, TypeScript production build, lint, and Vitest suite |
| Container matrix | Every service's real multi-stage Dockerfile builds |
| Compose validation | The complete local topology resolves with `.env.example` |
| Kubernetes validation | Every plain manifest passes strict offline Kubernetes schema validation |
| Tagged delivery | A `v*` tag produces a commit-addressed source/deployment bundle and manifest |

Backend test reports are retained on failure. A tagged build is uploaded as a GitHub Actions
artifact for 30 days and includes the version, commit SHA, UTC build time, and supported deployment
targets.

## Release procedure

1. Merge only after every required CI gate is green.
2. Create an annotated semantic version tag, for example `git tag -a v1.0.0 -m "Meridian Bank v1.0.0"`.
3. Push the tag with `git push origin v1.0.0`.
4. Download the `meridian-bank-v1.0.0` artifact from the tagged workflow run.
5. Deploy locally with Docker Compose or load the built service images into a local Kubernetes
   cluster using `infrastructure/kubernetes/README.md`.

## Intentional boundary

The workflow does not publish images, alter a cluster, or require repository secrets. Adding an
actual deployment job requires an explicitly chosen registry and environment, environment-scoped
credentials, protected approvals, rollback policy, and ownership. Until those exist, automated
deployment would be misleading and unsafe.
