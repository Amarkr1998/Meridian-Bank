package com.meridianbank.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendBeneficiaryVerificationResponse(long expiresInSeconds, String devOtp) {
}
