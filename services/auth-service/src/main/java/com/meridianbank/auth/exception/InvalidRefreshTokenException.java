package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthException {
    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired, or has already been used");
    }
}
