package com.meridianbank.ledger.service;

import com.meridianbank.ledger.config.LedgerProperties;
import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.domain.EntryType;
import com.meridianbank.ledger.domain.LedgerEntry;
import com.meridianbank.ledger.exception.InsufficientBalanceException;
import com.meridianbank.ledger.exception.InvalidPostingException;
import com.meridianbank.ledger.repository.BalanceRepository;
import com.meridianbank.ledger.repository.LedgerEntryRepository;
import com.meridianbank.ledger.web.dto.PostingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Posts a balanced double-entry pair — see docs/adr/0007-double-entry-ledger.md — inside one
 * database transaction with pessimistic row locks on both accounts' {@link Balance} rows, which
 * is this project's concurrency-safety mechanism for the ledger (see
 * docs/adr/0008-concurrency-strategy.md). This is the single place in the whole system money
 * actually moves — everything upstream (payment-service's validation pipeline) exists to decide
 * *whether* to call this; this is what makes it real.
 *
 * <p><b>Deadlock avoidance:</b> both balance rows are always locked in a fixed order (the lower
 * UUID first) regardless of which is the debit or credit side. Without this, two concurrent
 * transfers in opposite directions between the same two accounts (A→B and B→A) could deadlock —
 * one holding A's lock while waiting for B's, the other holding B's while waiting for A's.
 *
 * <p><b>Insufficient-balance protection:</b> the debit account's available balance is checked
 * only after its row is locked, so no other transaction can change it between the check and the
 * post — this is what actually prevents a double-spend / overdraft under concurrent transfers
 * from the same account, proven in {@code LedgerPostingServiceConcurrencyTest}.
 */
@Service
public class LedgerPostingService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceRepository balanceRepository;
    private final LedgerProperties properties;

    public LedgerPostingService(LedgerEntryRepository ledgerEntryRepository, BalanceRepository balanceRepository,
                                 LedgerProperties properties) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.balanceRepository = balanceRepository;
        this.properties = properties;
    }

    @Transactional
    public PostingResponse post(UUID transactionId, UUID debitAccountId, UUID creditAccountId, BigDecimal amount,
                                 String currency, String reference) {
        if (debitAccountId.equals(creditAccountId)) {
            throw new InvalidPostingException("Debit and credit accounts must be different");
        }

        List<LedgerEntry> existing = ledgerEntryRepository.findByTransactionId(transactionId);
        if (!existing.isEmpty()) {
            return replay(existing, debitAccountId, creditAccountId);
        }

        boolean debitFirst = debitAccountId.compareTo(creditAccountId) < 0;
        UUID firstId = debitFirst ? debitAccountId : creditAccountId;
        UUID secondId = debitFirst ? creditAccountId : debitAccountId;

        Balance first = getOrCreateForUpdate(firstId, currency);
        Balance second = getOrCreateForUpdate(secondId, currency);

        Balance debitBalance = debitFirst ? first : second;
        Balance creditBalance = debitFirst ? second : first;

        if (!debitBalance.hasSufficientAvailableBalance(amount)) {
            throw new InsufficientBalanceException();
        }

        debitBalance.debit(amount);
        creditBalance.credit(amount);
        balanceRepository.save(debitBalance);
        balanceRepository.save(creditBalance);

        LedgerEntry debitEntry = new LedgerEntry(transactionId, debitAccountId, EntryType.DEBIT, amount, currency, reference);
        LedgerEntry creditEntry = new LedgerEntry(transactionId, creditAccountId, EntryType.CREDIT, amount, currency, reference);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return new PostingResponse(transactionId, debitEntry.getLedgerEntryId(), creditEntry.getLedgerEntryId(),
                debitBalance.getAvailableBalance(), creditBalance.getAvailableBalance());
    }

    /** Idempotent replay: this transactionId was already posted (see docs/adr/0006-idempotency.md
     *  — payment-service should only ever call this once per transaction, but a network retry on
     *  its side must not double-post). */
    private PostingResponse replay(List<LedgerEntry> existing, UUID debitAccountId, UUID creditAccountId) {
        LedgerEntry debitEntry = existing.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).findFirst()
                .orElseThrow(() -> new IllegalStateException("Existing posting missing its DEBIT entry"));
        LedgerEntry creditEntry = existing.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).findFirst()
                .orElseThrow(() -> new IllegalStateException("Existing posting missing its CREDIT entry"));
        BigDecimal debitBalance = balanceRepository.findById(debitAccountId)
                .map(Balance::getAvailableBalance).orElse(BigDecimal.ZERO);
        BigDecimal creditBalance = balanceRepository.findById(creditAccountId)
                .map(Balance::getAvailableBalance).orElse(BigDecimal.ZERO);
        return new PostingResponse(debitEntry.getTransactionId(), debitEntry.getLedgerEntryId(),
                creditEntry.getLedgerEntryId(), debitBalance, creditBalance);
    }

    private Balance getOrCreateForUpdate(UUID accountId, String currency) {
        balanceRepository.ensureExists(accountId, currency != null ? currency : properties.defaultCurrency());
        return balanceRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("Balance row missing immediately after ensureExists"));
    }
}
