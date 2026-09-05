package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.exception.KycException;
import org.springframework.http.HttpStatus;

public class DuplicatePendingApprovalException extends KycException {
    public DuplicatePendingApprovalException() {
        super("DUPLICATE_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "A pending approval request already exists for this action on this resource");
    }
}
