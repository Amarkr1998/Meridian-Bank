# Customer Onboarding Flow

Covers registration through digital banking access. KYC review detail is expanded in
[kyc-flow.md](kyc-flow.md).

> **Implementation status (Phase 4):** registration through an active account is implemented and
> verified end-to-end across `auth-service` + `customer-kyc-service` + `account-service` — see
> [services/account-service/README.md](../../services/account-service/README.md). Balance and
> transaction history are explicitly not part of this (they need `ledger-service`/
> `payment-service`, Phases 6–7). Kafka events, audit writes, and notifications shown below are
> the target end-state (Phases 8, 11, 13).

## State Flow

```text
Registration
     ↓
Email/Mobile Verification
     ↓
Customer Profile
     ↓
KYC Submission
     ↓
KYC Review
     ↓
Risk Assessment
     ↓
Customer Approval
     ↓
Account Opening Request
     ↓
Account Approval
     ↓
Account Activation
     ↓
Digital Banking Access
```

Customer statuses: `ACTIVE`, `INACTIVE`, `BLOCKED`, `SUSPENDED`.
KYC statuses: `KYC_PENDING`, `KYC_IN_REVIEW`, `KYC_VERIFIED`, `KYC_REJECTED`.
Account request states: `ACCOUNT_REQUESTED` → `UNDER_REVIEW` → `APPROVED` → `ACTIVE`.

## Sequence Diagram

```mermaid
sequenceDiagram
    actor C as Customer
    participant WEB as Customer Portal
    participant GW as API Gateway
    participant AUTH as auth-service
    participant KYC as customer-kyc-service
    participant FRAUD as fraud-risk-service
    participant ACC as account-service
    participant KAFKA as Kafka
    participant NOTIF as notification-service
    participant AUDIT as audit-service

    C->>WEB: Fill registration form
    WEB->>GW: POST /api/v1/customers
    GW->>KYC: Create customer (PENDING verification)
    KYC-->>WEB: customerId + verification required

    WEB->>GW: Submit OTP (email/mobile)
    GW->>KYC: Verify contact
    KYC->>KAFKA: customer.created
    KAFKA-->>AUDIT: CUSTOMER_REGISTERED
    KAFKA-->>NOTIF: welcome notification (simulated)

    C->>WEB: Submit KYC details + document metadata
    WEB->>GW: POST /api/v1/kyc
    GW->>KYC: Create KYC record (KYC_PENDING)
    KYC->>KAFKA: kyc.updated (KYC_IN_REVIEW)
    KAFKA-->>AUDIT: KYC_SUBMITTED

    Note over KYC,FRAUD: Compliance officer reviews (Meridian Operations & Compliance)
    KYC->>FRAUD: Request risk assessment
    FRAUD-->>KYC: Risk score + recommendation
    KYC->>KYC: Approve or reject (KYC_VERIFIED / KYC_REJECTED)
    KYC->>KAFKA: kyc.updated
    KAFKA-->>AUDIT: KYC_APPROVED / KYC_REJECTED
    KAFKA-->>NOTIF: KYC_STATUS_CHANGED

    alt KYC_VERIFIED
        C->>WEB: Request account opening
        WEB->>GW: POST /api/v1/accounts/requests
        GW->>ACC: Create request (ACCOUNT_REQUESTED)
        ACC->>KYC: Confirm KYC_VERIFIED
        ACC->>ACC: UNDER_REVIEW → APPROVED → ACTIVE
        ACC->>KAFKA: account.created / account.approved
        KAFKA-->>AUDIT: ACCOUNT_CREATED
        KAFKA-->>NOTIF: ACCOUNT_STATUS_CHANGED
        ACC-->>WEB: Account active — digital banking access enabled
    else KYC_REJECTED
        KYC-->>WEB: Rejection reason shown; customer may resubmit
    end
```

## Notes

- Account opening is **never** automatic on registration — it is a distinct, gated request that
  requires `KYC_VERIFIED` status first (see [CLAUDE.md](../../CLAUDE.md)).
- All identity/document data used in this flow is synthetic; no real documents are stored, only
  metadata (see [Governance Principles](../governance/governance-principles.md)).
