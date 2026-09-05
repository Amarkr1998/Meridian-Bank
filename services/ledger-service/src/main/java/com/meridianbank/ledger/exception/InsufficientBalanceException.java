package com.meridianbank.ledger.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends LedgerException {
    public InsufficientBalanceException() {
        super("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT,
                "The debit account does not have sufficient available balance");
    }
}
