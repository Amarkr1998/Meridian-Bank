package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.config.AccountDefaultsProperties;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.exception.InvalidAccountStatusTransitionException;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.repository.AccountStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountStatusHistoryRepository statusHistoryRepository;
    @Mock private AccountNumberGenerator accountNumberGenerator;
    @Mock private AuditEventPublisher auditEventPublisher;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        AccountDefaultsProperties defaults = new AccountDefaultsProperties(
                "USD", new BigDecimal("5000.00"), new BigDecimal("20000.00"));
        accountService = new AccountService(accountRepository, statusHistoryRepository, accountNumberGenerator,
                defaults, auditEventPublisher);
    }

    private Account activeAccount() {
        return new Account(UUID.randomUUID(), "1234567890", AccountType.SAVINGS, "USD",
                new BigDecimal("5000.00"), new BigDecimal("20000.00"));
    }

    @Test
    void open_generatesAccountWithDefaultLimits() {
        when(accountNumberGenerator.generate()).thenReturn("9988776655");
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account account = accountService.open(UUID.randomUUID(), AccountType.CURRENT);

        assertThat(account.getAccountNumber()).isEqualTo("9988776655");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getPerTransactionLimit()).isEqualByComparingTo("5000.00");
        assertThat(account.getDailyLimit()).isEqualByComparingTo("20000.00");
    }

    @Test
    void freeze_fromActive_succeeds() {
        Account account = activeAccount();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account frozen = accountService.freeze(account.getId(), "suspicious activity", UUID.randomUUID());

        assertThat(frozen.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void freeze_fromClosed_throws() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThrows(InvalidAccountStatusTransitionException.class,
                () -> accountService.freeze(account.getId(), "reason", UUID.randomUUID()));
    }

    @Test
    void unfreeze_fromActive_throwsBecauseNotFrozen() {
        Account account = activeAccount(); // still ACTIVE
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThrows(InvalidAccountStatusTransitionException.class,
                () -> accountService.unfreeze(account.getId(), "reason", UUID.randomUUID()));
    }

    @Test
    void close_fromFrozen_succeedsAndSetsClosedAt() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account closed = accountService.close(account.getId(), "customer requested closure", UUID.randomUUID());

        assertThat(closed.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    void close_fromClosed_throwsBecauseTerminal() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThrows(InvalidAccountStatusTransitionException.class,
                () -> accountService.close(account.getId(), "reason", UUID.randomUUID()));
    }

    @Test
    void block_fromFrozen_succeeds() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account blocked = accountService.block(account.getId(), "fraud investigation", UUID.randomUUID());

        assertThat(blocked.getStatus()).isEqualTo(AccountStatus.BLOCKED);
    }
}
