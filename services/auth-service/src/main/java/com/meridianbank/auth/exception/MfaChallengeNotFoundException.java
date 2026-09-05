package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class MfaChallengeNotFoundException extends AuthException {
    public MfaChallengeNotFoundException() {
        super("MFA_CHALLENGE_EXPIRED", HttpStatus.BAD_REQUEST,
                "MFA challenge not found or has expired — please log in again");
    }
}
