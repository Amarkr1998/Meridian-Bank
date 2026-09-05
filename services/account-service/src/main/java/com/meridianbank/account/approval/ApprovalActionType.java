package com.meridianbank.account.approval;

/**
 * The fixed set of account-service actions gated by maker-checker — see
 * docs/governance/governance-principles.md ("account block/unblock"). This service has no
 * unblock action (BLOCKED is currently a one-way transition — see AccountService), so only BLOCK
 * is gated; freeze/unfreeze/close remain single-actor, unchanged.
 */
public enum ApprovalActionType {
    BLOCK_ACCOUNT
}
