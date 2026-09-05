package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends AuthException {
    public AccountLockedException() {
        super("ACCOUNT_LOCKED", HttpStatus.FORBIDDEN,
                "Account is temporarily locked due to repeated failed login attempts");
    }
}
