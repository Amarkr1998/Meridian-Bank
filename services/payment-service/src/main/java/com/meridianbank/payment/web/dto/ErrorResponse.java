package com.meridianbank.payment.web.dto;

/** Standard API error envelope — see docs/api/api-governance.md. */
public record ErrorResponse(String code, String message, String correlationId) {
}
