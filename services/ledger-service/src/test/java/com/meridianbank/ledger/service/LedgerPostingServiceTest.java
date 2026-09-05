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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private BalanceRepository balanceRepository;

    private LedgerPostingService postingService;

    @BeforeEach
    void setUp() {
        postingService = new LedgerPostingService(ledgerEntryRepository, balanceRepository, new LedgerProperties("USD"));
    }

    @Test
    void post_debitAndCreditSameAccount_throws() {
        UUID accountId = UUID.randomUUID();
        assertThrows(InvalidPostingException.class,
                () -> postingService.post(UUID.randomUUID(), accountId, accountId, BigDecimal.TEN, "USD", null));
        verifyNoInteractions(ledgerEntryRepository, balanceRepository);
    }

    @Test
    void post_alreadyPostedTransaction_replaysWithoutReposting() {
        UUID transactionId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        LedgerEntry debitEntry = new LedgerEntry(transactionId, debitAccountId, EntryType.DEBIT, BigDecimal.TEN, "USD", null);
        LedgerEntry creditEntry = new LedgerEntry(transactionId, creditAccountId, EntryType.CREDIT, BigDecimal.TEN, "USD", null);
        when(ledgerEntryRepository.findByTransactionId(transactionId)).thenReturn(List.of(debitEntry, creditEntry));
        when(balanceRepository.findById(debitAccountId))
                .thenReturn(Optional.of(balanceWith(debitAccountId, "90.00")));
        when(balanceRepository.findById(creditAccountId))
                .thenReturn(Optional.of(balanceWith(creditAccountId, "10.00")));

        PostingResponse response = postingService.post(transactionId, debitAccountId, creditAccountId, BigDecimal.TEN, "USD", null);

        assertThat(response.debitEntryId()).isEqualTo(debitEntry.getLedgerEntryId());
        assertThat(response.creditEntryId()).isEqualTo(creditEntry.getLedgerEntryId());
        verify(balanceRepository, never()).findByIdForUpdate(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void post_withInsufficientBalance_throwsAndPostsNothing() {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        when(ledgerEntryRepository.findByTransactionId(any())).thenReturn(List.of());
        when(balanceRepository.findByIdForUpdate(any()))
                .thenAnswer(inv -> Optional.of(balanceWith(inv.getArgument(0), "5.00")));

        assertThrows(InsufficientBalanceException.class, () -> postingService.post(UUID.randomUUID(),
                debitAccountId, creditAccountId, new BigDecimal("10.00"), "USD", null));

        verify(ledgerEntryRepository, never()).save(any());
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void post_withSufficientBalance_createsBalancedEntriesAndUpdatesBothBalances() {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        when(ledgerEntryRepository.findByTransactionId(any())).thenReturn(List.of());
        when(balanceRepository.findByIdForUpdate(debitAccountId))
                .thenReturn(Optional.of(balanceWith(debitAccountId, "100.00")));
        when(balanceRepository.findByIdForUpdate(creditAccountId))
                .thenReturn(Optional.of(balanceWith(creditAccountId, "20.00")));

        PostingResponse response = postingService.post(UUID.randomUUID(), debitAccountId, creditAccountId,
                new BigDecimal("30.00"), "USD", "test");

        assertThat(response.debitAccountAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(response.creditAccountAvailableBalance()).isEqualByComparingTo("50.00");
        verify(ledgerEntryRepository, times(2)).save(any());
        verify(balanceRepository, times(2)).save(any());
    }

    @Test
    void post_locksAccountsInAFixedOrder_regardlessOfDebitCreditDirection() {
        // UUID.compareTo() compares the internal fields as SIGNED longs, so which of two UUIDs
        // sorts first isn't predictable by inspection — compute it the same way the service does
        // rather than assuming a naive lexicographic order.
        UUID accountX = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID accountY = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID expectedFirst = accountX.compareTo(accountY) < 0 ? accountX : accountY;
        UUID expectedSecond = expectedFirst.equals(accountX) ? accountY : accountX;

        when(ledgerEntryRepository.findByTransactionId(any())).thenReturn(List.of());
        when(balanceRepository.findByIdForUpdate(any()))
                .thenAnswer(inv -> Optional.of(balanceWith(inv.getArgument(0), "1000.00")));

        // Post with accountY as the debit side — the lock order must still follow the fixed
        // ordering above, not "debit first," which is the property that prevents deadlocks
        // between two transfers in opposite directions (see LedgerPostingConcurrencyTest).
        postingService.post(UUID.randomUUID(), accountY, accountX, BigDecimal.ONE, "USD", null);

        var inOrder = inOrder(balanceRepository);
        inOrder.verify(balanceRepository).findByIdForUpdate(expectedFirst);
        inOrder.verify(balanceRepository).findByIdForUpdate(expectedSecond);
    }

    private Balance balanceWith(UUID accountId, String amount) {
        Balance balance = new Balance(accountId, "USD");
        balance.credit(new BigDecimal(amount));
        return balance;
    }
}
