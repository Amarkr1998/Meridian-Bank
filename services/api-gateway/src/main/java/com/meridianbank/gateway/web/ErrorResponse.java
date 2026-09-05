package com.meridianbank.gateway.web;

/** Standard error envelope — see docs/api/api-governance.md. */
public record ErrorResponse(String code, String message, String correlationId) {
}
