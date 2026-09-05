# Architecture Documentation

Index of architecture documentation for Meridian Bank. Start with
[system-architecture.md](system-architecture.md) for the overall picture, then follow the flow
documents for how each domain scenario plays out across services.

- [system-architecture.md](system-architecture.md) — component diagram, service responsibilities,
  domain entity overview, synchronous vs. asynchronous communication
- [onboarding-flow.md](onboarding-flow.md) — customer registration → KYC → account activation
- [kyc-flow.md](kyc-flow.md) — KYC submission and compliance review detail
- [payment-flow.md](payment-flow.md) — end-to-end payment processing, including idempotency
- [fraud-flow.md](fraud-flow.md) — fraud rule evaluation and risk scoring
- [aml-flow.md](aml-flow.md) — simplified AML monitoring and alerting
- [maker-checker-flow.md](maker-checker-flow.md) — four-eyes approval workflow
- [reconciliation-flow.md](reconciliation-flow.md) — ledger vs. synthetic external record reconciliation
- [kafka-architecture.md](kafka-architecture.md) — topics, producers/consumers, outbox, DLQ
- [deployment-architecture.md](deployment-architecture.md) — local Docker Compose and Kubernetes topology

Related documentation:

- [Security Architecture](../security/security-architecture.md)
- [Data Governance](../governance/governance-principles.md)
- [Database / Domain Model](../database/domain-model.md)
- [API Governance](../api/api-governance.md)
- [Reconciliation Design](../reconciliation/reconciliation-design.md)
- [Architecture Decision Records](../adr/README.md)
