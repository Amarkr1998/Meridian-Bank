package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordResetTokenException extends AuthException {
    public InvalidPasswordResetTokenException() {
        super("INVALID_PASSWORD_RESET_TOKEN", HttpStatus.BAD_REQUEST,
                "Password reset token is invalid, expired, or has already been used");
    }
}
