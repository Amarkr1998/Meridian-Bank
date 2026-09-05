package com.meridianbank.gateway.security;

import com.meridianbank.gateway.config.GatewayCorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hand-rolled rather than Spring's {@code CorsFilter} bean, to keep this gateway's whole request
 * pipeline as one readable, consistently-ordered stack of plain servlet filters (see the other
 * filters in this package) instead of mixing in a second configuration mechanism for just one
 * concern. Runs first (Order 0) so a preflight is answered before correlation IDs, rate limiting,
 * or edge auth ever run, and so CORS headers land on every response this gateway produces,
 * including the error responses those later filters write.
 */
@Component
@Order(0)
public class CorsFilter extends OncePerRequestFilter {

    private final GatewayCorsProperties properties;

    public CorsFilter(GatewayCorsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && properties.allowedOrigins().contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,Idempotency-Key,X-Correlation-Id");
            response.setHeader("Access-Control-Expose-Headers", "X-Correlation-Id");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
