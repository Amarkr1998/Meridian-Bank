package com.meridianbank.kyc.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendVerificationResponse(long expiresInSeconds, String devOtp) {
}
