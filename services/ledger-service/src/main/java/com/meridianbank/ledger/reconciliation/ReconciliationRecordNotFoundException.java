package com.meridianbank.ledger.reconciliation;

import com.meridianbank.ledger.exception.LedgerException;
import org.springframework.http.HttpStatus;

public class ReconciliationRecordNotFoundException extends LedgerException {

    public ReconciliationRecordNotFoundException() {
        super("RECONCILIATION_RECORD_NOT_FOUND", HttpStatus.NOT_FOUND, "Reconciliation record not found");
    }
}
