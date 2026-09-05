package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends AccountException {
    public AccountNotFoundException() {
        super("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Account not found");
    }
}
