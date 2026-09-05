package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditAction;
import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.config.AccountDefaultsProperties;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.domain.AccountStatusHistory;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.exception.AccountNotFoundException;
import com.meridianbank.account.exception.InvalidAccountStatusTransitionException;
import com.meridianbank.account.repository.AccountRepository;
import com.meridianbank.account.repository.AccountStatusHistoryRepository;
import com.meridianbank.account.web.dto.AccountStatusHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Account lifecycle: ACTIVE ⇄ FROZEN, (ACTIVE|FROZEN) → BLOCKED, any non-CLOSED → CLOSED
 * (terminal). See docs/architecture (account states) and account-service/README.md. Balance is
 * not owned here — see {@link Account}.
 */
@Service
public class AccountService {

    private static final Set<AccountStatus> FREEZE_FROM = EnumSet.of(AccountStatus.ACTIVE);
    private static final Set<AccountStatus> UNFREEZE_FROM = EnumSet.of(AccountStatus.FROZEN);
    private static final Set<AccountStatus> BLOCK_FROM = EnumSet.of(AccountStatus.ACTIVE, AccountStatus.FROZEN);
    private static final Set<AccountStatus> CLOSE_FROM =
            EnumSet.of(AccountStatus.ACTIVE, AccountStatus.FROZEN, AccountStatus.BLOCKED);

    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository statusHistoryRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountDefaultsProperties defaults;
    private final AuditEventPublisher auditEventPublisher;

    public AccountService(AccountRepository accountRepository, AccountStatusHistoryRepository statusHistoryRepository,
                           AccountNumberGenerator accountNumberGenerator, AccountDefaultsProperties defaults,
                           AuditEventPublisher auditEventPublisher) {
        this.accountRepository = accountRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.defaults = defaults;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public Account open(UUID customerId, AccountType accountType) {
        Account account = new Account(customerId, accountNumberGenerator.generate(), accountType,
                defaults.defaultCurrency(), defaults.defaultPerTransactionLimit(), defaults.defaultDailyLimit());
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getOrThrow(UUID id) {
        return accountRepository.findById(id).orElseThrow(AccountNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Account> list(UUID customerId, AccountStatus status, Pageable pageable) {
        if (customerId != null && status != null) {
            return accountRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return accountRepository.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return accountRepository.findByStatus(status, pageable);
        }
        return accountRepository.findAll(pageable);
    }

    @Transactional
    public Account freeze(UUID id, String reason, UUID actorId) {
        Account account = transition(id, AccountStatus.FROZEN, FREEZE_FROM, reason, actorId);
        auditEventPublisher.record(AuditAction.ACCOUNT_FROZEN, "account", id, actorId, null, "SUCCESS", reason);
        return account;
    }

    @Transactional
    public Account unfreeze(UUID id, String reason, UUID actorId) {
        return transition(id, AccountStatus.ACTIVE, UNFREEZE_FROM, reason, actorId);
    }

    @Transactional
    public Account block(UUID id, String reason, UUID actorId) {
        Account account = transition(id, AccountStatus.BLOCKED, BLOCK_FROM, reason, actorId);
        auditEventPublisher.record(AuditAction.ACCOUNT_BLOCKED, "account", id, actorId, null, "SUCCESS", reason);
        return account;
    }

    @Transactional
    public Account close(UUID id, String reason, UUID actorId) {
        Account account = transition(id, AccountStatus.CLOSED, CLOSE_FROM, reason, actorId);
        account.setClosedAt(java.time.Instant.now());
        return accountRepository.save(account);
    }

    @Transactional
    public Account updateLimits(UUID id, BigDecimal perTransactionLimit, BigDecimal dailyLimit) {
        Account account = getOrThrow(id);
        account.setPerTransactionLimit(perTransactionLimit);
        account.setDailyLimit(dailyLimit);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<AccountStatusHistoryResponse> getStatusHistory(UUID accountId) {
        return statusHistoryRepository.findByAccountIdOrderByChangedAtDesc(accountId).stream()
                .map(AccountStatusHistoryResponse::from)
                .toList();
    }

    private Account transition(UUID id, AccountStatus target, Set<AccountStatus> allowedFrom,
                                String reason, UUID actorId) {
        Account account = getOrThrow(id);
        AccountStatus current = account.getStatus();
        if (!allowedFrom.contains(current)) {
            throw new InvalidAccountStatusTransitionException(
                    "Cannot move an account from " + current + " to " + target);
        }
        account.setStatus(target);
        accountRepository.save(account);
        statusHistoryRepository.save(new AccountStatusHistory(id, current, target, reason, actorId));
        return account;
    }
}
