package com.meridianbank.ledger.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * See auth-service's JwtAuthenticationFilter — identical verification pattern. Unlike
 * account-service/payment-service, this service makes no further outbound calls, so it doesn't
 * need to stash the raw bearer token — only proof of authentication matters here. Ownership of
 * "which customer owns which account" is deliberately NOT enforced at this layer — see
 * ledger-service/README.md's Trust Boundary section.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtVerifier.DecodedAccessToken decoded = jwtVerifier.verify(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + decoded.role()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        decoded.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid/expired token: leave the context unauthenticated; downstream
                // authorization rules will reject the request with 401/403 as appropriate.
            }
        }
        filterChain.doFilter(request, response);
    }
}
