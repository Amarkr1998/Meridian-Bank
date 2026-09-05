package com.meridianbank.auth.security;

import com.meridianbank.auth.service.JwtService;
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
 * Validates the {@code Authorization: Bearer <token>} header, if present, and populates the
 * SecurityContext with the caller's userId (as principal) and role (as a {@code ROLE_*} authority)
 * so {@code @PreAuthorize}/{@code hasRole(...)} checks work downstream. Absence of a token is not
 * an error here — unauthenticated requests are rejected later by the filter chain's authorization
 * rules for any endpoint that requires authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtService.DecodedAccessToken decoded = jwtService.parseAccessToken(token);
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
