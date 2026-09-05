package com.meridianbank.gateway.security;

import com.meridianbank.gateway.config.RateLimitProperties;
import com.meridianbank.gateway.web.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Fixed-window counter per client IP, Redis-backed (ADR-0009). Deliberately keyed by IP rather
 * than by the caller's JWT subject — this filter runs before edge auth, so it works uniformly for
 * both public (login/register) and authenticated routes, which is exactly where credential-
 * stuffing / registration-spam abuse needs slowing down most.
 *
 * <p>Fails open on a Redis error: a rate limiter that goes down should not take the whole edge
 * down with it (same reasoning as this service's health check excluding Redis — see
 * application.yml).
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String KEY_PREFIX = "gateway:ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final ErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(StringRedisTemplate redisTemplate, RateLimitProperties properties,
                            ErrorResponseWriter errorResponseWriter) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            long window = System.currentTimeMillis() / 1000 / properties.windowSeconds();
            String key = KEY_PREFIX + clientKey(request) + ":" + window;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(properties.windowSeconds() + 1L));
            }
            if (count != null && count > properties.maxRequestsPerWindow()) {
                errorResponseWriter.write(request, response, 429,
                        "RATE_LIMIT_EXCEEDED", "Too many requests — please slow down");
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, failing open: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
