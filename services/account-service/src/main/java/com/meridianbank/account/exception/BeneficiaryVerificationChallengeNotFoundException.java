package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class BeneficiaryVerificationChallengeNotFoundException extends AccountException {
    public BeneficiaryVerificationChallengeNotFoundException() {
        super("VERIFICATION_CHALLENGE_EXPIRED", HttpStatus.BAD_REQUEST,
                "Verification code not found or has expired — please request a new one");
    }
}
