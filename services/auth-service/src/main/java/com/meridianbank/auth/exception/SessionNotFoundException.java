package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends AuthException {
    public SessionNotFoundException() {
        super("SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "Session not found");
    }
}
