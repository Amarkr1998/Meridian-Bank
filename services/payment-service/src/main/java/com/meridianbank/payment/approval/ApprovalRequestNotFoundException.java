package com.meridianbank.payment.approval;

import com.meridianbank.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;

public class ApprovalRequestNotFoundException extends PaymentException {
    public ApprovalRequestNotFoundException() {
        super("APPROVAL_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Approval request not found");
    }
}
