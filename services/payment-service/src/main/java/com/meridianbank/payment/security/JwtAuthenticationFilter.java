package com.meridianbank.payment.security;

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
 * See auth-service's JwtAuthenticationFilter — identical pattern. Also stashes the raw bearer
 * token as a request attribute: AccountServiceClient/CustomerKycServiceClient forward it when
 * validating the source account, beneficiary, and KYC status, since those checks are themselves
 * resource-owned on the downstream side.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_TOKEN_ATTRIBUTE = "bearerToken";

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
                request.setAttribute(BEARER_TOKEN_ATTRIBUTE, token);
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid/expired token: leave the context unauthenticated; downstream
                // authorization rules will reject the request with 401/403 as appropriate.
            }
        }
        filterChain.doFilter(request, response);
    }
}
