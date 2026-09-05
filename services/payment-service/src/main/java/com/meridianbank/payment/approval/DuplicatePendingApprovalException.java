package com.meridianbank.payment.approval;

import com.meridianbank.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;

public class DuplicatePendingApprovalException extends PaymentException {
    public DuplicatePendingApprovalException() {
        super("DUPLICATE_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "A pending approval request already exists for this action on this resource");
    }
}
