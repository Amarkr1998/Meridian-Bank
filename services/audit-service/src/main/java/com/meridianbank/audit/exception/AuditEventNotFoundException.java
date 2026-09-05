package com.meridianbank.audit.exception;

import org.springframework.http.HttpStatus;

public class AuditEventNotFoundException extends AuditServiceException {

    public AuditEventNotFoundException() {
        super("AUDIT_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Audit event not found");
    }
}
