package com.meridianbank.account.approval;

import com.meridianbank.account.exception.AccountException;
import org.springframework.http.HttpStatus;

/** The server-side enforcement of "the maker can never approve their own request" —
 *  see docs/adr/0011-maker-checker.md. Not just a UI restriction. */
public class SelfApprovalNotAllowedException extends AccountException {
    public SelfApprovalNotAllowedException() {
        super("SELF_APPROVAL_NOT_ALLOWED", HttpStatus.FORBIDDEN,
                "The maker of an approval request cannot also decide it");
    }
}
