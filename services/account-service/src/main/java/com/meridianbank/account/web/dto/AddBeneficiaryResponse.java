package com.meridianbank.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code devOtp} stands in for the email/SMS verification channel until notification-service
 * (Phase 13) exists — same documented pattern as auth-service's MFA. Only populated in demo mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddBeneficiaryResponse(BeneficiaryResponse beneficiary, long verificationExpiresInSeconds, String devOtp) {
}
