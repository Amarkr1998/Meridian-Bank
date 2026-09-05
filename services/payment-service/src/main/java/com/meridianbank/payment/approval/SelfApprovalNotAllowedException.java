package com.meridianbank.payment.approval;

import com.meridianbank.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;

/** The server-side enforcement of "the maker can never approve their own request" —
 *  see docs/adr/0011-maker-checker.md. Not just a UI restriction. */
public class SelfApprovalNotAllowedException extends PaymentException {
    public SelfApprovalNotAllowedException() {
        super("SELF_APPROVAL_NOT_ALLOWED", HttpStatus.FORBIDDEN,
                "The maker of an approval request cannot also decide it");
    }
}
