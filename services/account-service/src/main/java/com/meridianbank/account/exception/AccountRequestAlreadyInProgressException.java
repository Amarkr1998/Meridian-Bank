package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class AccountRequestAlreadyInProgressException extends AccountException {
    public AccountRequestAlreadyInProgressException() {
        super("ACCOUNT_REQUEST_ALREADY_IN_PROGRESS", HttpStatus.CONFLICT,
                "An account opening request of this type is already pending or under review");
    }
}
