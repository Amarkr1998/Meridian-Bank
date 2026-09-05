package com.meridianbank.account.approval;

import com.meridianbank.account.exception.AccountException;
import org.springframework.http.HttpStatus;

public class DuplicatePendingApprovalException extends AccountException {
    public DuplicatePendingApprovalException() {
        super("DUPLICATE_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "A pending approval request already exists for this action on this resource");
    }
}
