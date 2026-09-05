package com.meridianbank.fraud.approval;

import com.meridianbank.fraud.exception.FraudRiskException;
import org.springframework.http.HttpStatus;

public class DuplicatePendingApprovalException extends FraudRiskException {
    public DuplicatePendingApprovalException() {
        super("DUPLICATE_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "A pending approval request already exists for this action on this resource");
    }
}
