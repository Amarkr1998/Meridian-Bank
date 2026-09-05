package com.meridianbank.fraud.domain;

/** See docs/architecture/fraud-flow.md — 0-30 LOW/ALLOW, 31-70 MEDIUM/REVIEW, 71-100 HIGH/BLOCK. */
public enum RiskDecision {
    ALLOW,
    REVIEW,
    BLOCK
}
