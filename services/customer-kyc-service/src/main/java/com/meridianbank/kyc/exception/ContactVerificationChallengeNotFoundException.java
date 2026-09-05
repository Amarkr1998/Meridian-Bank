package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class ContactVerificationChallengeNotFoundException extends KycException {
    public ContactVerificationChallengeNotFoundException() {
        super("VERIFICATION_CHALLENGE_EXPIRED", HttpStatus.BAD_REQUEST,
                "Verification code not found or has expired — please request a new one");
    }
}
