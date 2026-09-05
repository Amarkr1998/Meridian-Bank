package com.meridianbank.kyc.support;

/** OPEN -> IN_PROGRESS -> RESOLVED, terminal — see V4__add_support_requests.sql's comment for why
 *  there is no reopen cycle. */
public enum SupportRequestStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED
}
