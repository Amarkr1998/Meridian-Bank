package com.meridianbank.account.service;

import com.meridianbank.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Synthetic 10-digit account numbers for this demo bank — never a real account numbering scheme. */
@Component
public class AccountNumberGenerator {

    private static final int LENGTH = 10;
    private static final int MAX_ATTEMPTS = 10;
    private final SecureRandom secureRandom = new SecureRandom();
    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomDigits();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique account number after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomDigits() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}
