package com.meridianbank.account.approval;

import com.meridianbank.account.exception.AccountException;
import org.springframework.http.HttpStatus;

public class ApprovalRequestNotFoundException extends AccountException {
    public ApprovalRequestNotFoundException() {
        super("APPROVAL_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Approval request not found");
    }
}
