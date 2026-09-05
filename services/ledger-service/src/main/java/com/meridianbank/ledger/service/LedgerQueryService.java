package com.meridianbank.ledger.service;

import com.meridianbank.ledger.config.LedgerProperties;
import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.domain.LedgerEntry;
import com.meridianbank.ledger.repository.BalanceRepository;
import com.meridianbank.ledger.repository.LedgerEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read paths — no locking, since nothing here mutates a balance. */
@Service
public class LedgerQueryService {

    private final BalanceRepository balanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerProperties properties;

    public LedgerQueryService(BalanceRepository balanceRepository, LedgerEntryRepository ledgerEntryRepository,
                               LedgerProperties properties) {
        this.balanceRepository = balanceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.properties = properties;
    }

    /** A never-before-seen account has an implicit zero balance — materialized on first inquiry
     *  rather than requiring some other service to have created it first. */
    @Transactional
    public Balance getBalance(UUID accountId) {
        balanceRepository.ensureExists(accountId, properties.defaultCurrency());
        return balanceRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Balance row missing immediately after ensureExists"));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> getEntries(UUID accountId, Pageable pageable) {
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }
}
