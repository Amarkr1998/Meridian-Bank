package com.meridianbank.fraud.approval;

import com.meridianbank.fraud.exception.FraudRiskException;
import org.springframework.http.HttpStatus;

/** The server-side enforcement of "the maker can never approve their own request" —
 *  see docs/adr/0011-maker-checker.md. Not just a UI restriction. */
public class SelfApprovalNotAllowedException extends FraudRiskException {
    public SelfApprovalNotAllowedException() {
        super("SELF_APPROVAL_NOT_ALLOWED", HttpStatus.FORBIDDEN,
                "The maker of an approval request cannot also decide it");
    }
}
