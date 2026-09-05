package com.meridianbank.account.web;

import com.meridianbank.account.exception.AccountNotFoundException;
import com.meridianbank.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test-only helper: reads the raw (unmasked) account number directly from the repository, since
 * the public API never exposes it (see AccountResponse) — needed purely to construct a valid
 * beneficiary target account number for black-box HTTP integration tests.
 */
@Component
class TestAccountNumberLookup {

    private final AccountRepository accountRepository;

    TestAccountNumberLookup(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    String rawNumberFor(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new).getAccountNumber();
    }
}
