package com.meridianbank.fraud.domain;

/**
 * FRAUD rules feed the blocking score returned to payment-service (see {@link RiskDecision}); AML
 * rules only raise {@code aml_alerts} for the compliance queue and never affect the payment's own
 * outcome — see docs/architecture/aml-flow.md and docs/governance/governance-principles.md's
 * "Simplified AML Disclaimer".
 */
public enum RuleCategory {
    FRAUD,
    AML
}
