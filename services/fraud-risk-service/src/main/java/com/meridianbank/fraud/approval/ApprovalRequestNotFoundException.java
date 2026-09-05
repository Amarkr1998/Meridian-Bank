package com.meridianbank.fraud.approval;

import com.meridianbank.fraud.exception.FraudRiskException;
import org.springframework.http.HttpStatus;

public class ApprovalRequestNotFoundException extends FraudRiskException {
    public ApprovalRequestNotFoundException() {
        super("APPROVAL_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Approval request not found");
    }
}
