package com.meridianbank.gateway.security;

import com.meridianbank.gateway.routing.RouteDefinition;
import com.meridianbank.gateway.routing.RouteRegistry;
import com.meridianbank.gateway.web.ErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Coarse-grained edge authorization only (ADR-0009): rejects a request with no/expired/malformed
 * token before it ever reaches a downstream service, for any route this gateway knows about that
 * isn't in that route's public list. It does not decode role or resource claims, and it is not the
 * authorization boundary — every downstream service still independently verifies the same token
 * and enforces its own RBAC/resource-ownership rules (see each service's SecurityConfig). A path
 * this gateway doesn't route at all is deliberately let through here — ProxyFilter is the one that
 * turns it into 404, not this filter.
 */
@Component
@Order(3)
public class EdgeAuthFilter extends OncePerRequestFilter {

    private final RouteRegistry routeRegistry;
    private final JwtVerifier jwtVerifier;
    private final ErrorResponseWriter errorResponseWriter;

    public EdgeAuthFilter(RouteRegistry routeRegistry, JwtVerifier jwtVerifier, ErrorResponseWriter errorResponseWriter) {
        this.routeRegistry = routeRegistry;
        this.jwtVerifier = jwtVerifier;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Optional<RouteDefinition> route = routeRegistry.resolve(path);

        if (route.isEmpty() || routeRegistry.isPublic(route.get(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Authentication is required");
            return;
        }
        try {
            jwtVerifier.verify(header.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Authentication is required");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
