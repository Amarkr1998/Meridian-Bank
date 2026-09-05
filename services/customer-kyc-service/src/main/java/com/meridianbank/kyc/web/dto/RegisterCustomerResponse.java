package com.meridianbank.kyc.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * {@code devOtp} stands in for the email/SMS verification channel until notification-service
 * (Phase 13) exists — see auth-service/README.md for the identical, documented pattern used for
 * MFA. Only populated in local/demo mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterCustomerResponse(UUID customerId, String email, boolean contactVerified,
                                        long verificationExpiresInSeconds, String devOtp) {
}
