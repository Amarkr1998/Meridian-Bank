package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends PaymentException {
    public TransactionNotFoundException() {
        super("TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND, "Transaction not found");
    }
}
