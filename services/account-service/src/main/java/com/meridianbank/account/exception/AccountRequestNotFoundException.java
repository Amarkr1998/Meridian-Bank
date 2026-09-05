package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class AccountRequestNotFoundException extends AccountException {
    public AccountRequestNotFoundException() {
        super("ACCOUNT_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Account opening request not found");
    }
}
