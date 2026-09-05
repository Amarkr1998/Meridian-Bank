package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends AuthException {
    public AccountNotActiveException() {
        super("ACCOUNT_NOT_ACTIVE", HttpStatus.FORBIDDEN, "Account is not active");
    }
}
