package com.meridianbank.kyc.domain;

/** Metadata only — never a real identity document. See docs/governance/governance-principles.md. */
public enum DocumentType {
    NATIONAL_ID,
    PASSPORT,
    DRIVERS_LICENSE,
    PROOF_OF_ADDRESS
}
