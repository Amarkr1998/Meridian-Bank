package com.meridianbank.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per account, materialized for fast reads but always derived from — and updated in the
 * same database transaction as — {@link LedgerEntry} rows (see LedgerPostingService). Never
 * mutated independently of a posting. {@code availableBalance} and {@code ledgerBalance} are kept
 * equal in this phase: there is no pending/hold concept yet (see ledger-service/README.md) — the
 * distinction exists in the schema because it's a standard real-banking concept worth modeling
 * even though nothing yet makes them diverge.
 */
@Entity
@Table(name = "balances")
public class Balance {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "ledger_balance", nullable = false)
    private BigDecimal ledgerBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Balance() {
    }

    public Balance(UUID accountId, String currency) {
        this.accountId = accountId;
        this.availableBalance = BigDecimal.ZERO.setScale(2);
        this.ledgerBalance = BigDecimal.ZERO.setScale(2);
        this.currency = currency;
        this.updatedAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.subtract(amount);
        this.ledgerBalance = this.ledgerBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
        this.ledgerBalance = this.ledgerBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    public boolean hasSufficientAvailableBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
