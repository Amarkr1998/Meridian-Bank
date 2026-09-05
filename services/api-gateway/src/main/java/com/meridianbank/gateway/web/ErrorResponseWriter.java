package com.meridianbank.gateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.gateway.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Shared by every filter that can short-circuit a request, so every edge-rejected call gets the
 * same envelope. Also the one place this gateway sets the baseline security response headers
 * (`X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control: no-store`) that every downstream
 * service already gets for free from Spring Security's defaults — this gateway has no Spring
 * Security at all (see README, "Why not Spring Cloud Gateway" — same minimalism reasoning), so its
 * own directly-generated responses (this class, and {@code SystemHealthController}) would
 * otherwise ship without them. A proxied response is unaffected: it already carries the
 * downstream service's own copies of these headers, forwarded unchanged by {@code ProxyFilter}.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        applyBaselineSecurityHeaders(response);
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(code, message, correlationId)));
    }

    public static void applyBaselineSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-store");
    }
}
