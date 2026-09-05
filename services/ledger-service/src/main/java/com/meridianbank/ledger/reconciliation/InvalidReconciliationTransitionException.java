package com.meridianbank.ledger.reconciliation;

import com.meridianbank.ledger.exception.LedgerException;
import org.springframework.http.HttpStatus;

public class InvalidReconciliationTransitionException extends LedgerException {

    public InvalidReconciliationTransitionException(String message) {
        super("INVALID_RECONCILIATION_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
