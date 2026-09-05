package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.exception.KycException;
import org.springframework.http.HttpStatus;

public class ApprovalRequestNotFoundException extends KycException {
    public ApprovalRequestNotFoundException() {
        super("APPROVAL_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Approval request not found");
    }
}
