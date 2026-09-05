package com.meridianbank.gateway.web;

import com.meridianbank.gateway.routing.RouteDefinition;
import com.meridianbank.gateway.routing.RouteRegistry;
import com.meridianbank.gateway.security.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The terminal filter: forwards a request whose path matched a {@link RouteDefinition} to that
 * service's base URL, byte-for-byte (method, headers, query string, body), and relays the
 * downstream response straight back (status, headers, body). A path under {@code /api/v1/} that
 * matches no route is this gateway's own 404, not a downstream one. Everything else (health
 * checks, anything outside {@code /api/v1/}) falls through to normal Spring dispatch.
 */
@Component
@Order(4)
public class ProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ProxyFilter.class);

    /** Hop-by-hop / connection-specific headers that must never be forwarded verbatim. */
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of("host", "content-length", "connection", "accept-encoding");
    /**
     * {@code x-correlation-id} is skipped on the way back because the gateway's own
     * {@link CorrelationIdFilter} already set it on this response before proxying began, and the
     * downstream service's echoed value is always the same one — see {@link #forward} for where
     * that value is forwarded on the way in.
     */
    private static final Set<String> SKIP_RESPONSE_HEADERS =
            Set.of("transfer-encoding", "connection", "content-length", "x-correlation-id");

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;
    private final ErrorResponseWriter errorResponseWriter;

    public ProxyFilter(RouteRegistry routeRegistry, RestClient upstreamRestClient, ErrorResponseWriter errorResponseWriter) {
        this.routeRegistry = routeRegistry;
        this.restClient = upstreamRestClient;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Optional<RouteDefinition> route = routeRegistry.resolve(path);

        if (route.isEmpty()) {
            if (path.startsWith("/api/v1/")) {
                writeNotFound(request, response);
            } else {
                filterChain.doFilter(request, response);
            }
            return;
        }

        if (route.get().baseUrl() == null) {
            // A route with no base URL is handled locally by a real @RestController in this
            // gateway (e.g. SystemHealthController) rather than proxied — EdgeAuthFilter still
            // required a valid token to get this far, exactly like any other non-public route.
            filterChain.doFilter(request, response);
            return;
        }

        forward(request, response, route.get(), path);
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, RouteDefinition route, String path)
            throws IOException {
        String queryString = request.getQueryString();
        String targetUrl = route.baseUrl() + route.rewritePath(path) + (queryString != null ? "?" + queryString : "");
        byte[] body = request.getInputStream().readAllBytes();

        try {
            String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
            RestClient.RequestBodySpec spec = restClient.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(targetUrl)
                    .headers(headers -> {
                        Collections.list(request.getHeaderNames()).forEach(name -> {
                            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                                Collections.list(request.getHeaders(name)).forEach(value -> headers.add(name, value));
                            }
                        });
                        // The incoming request may never have carried this header at all (the
                        // common case for a fresh browser request) — CorrelationIdFilter only
                        // records the ID it minted in a request attribute and this response's
                        // header, not on the (immutable) incoming request, so it must be set here
                        // explicitly or the ID silently fails to propagate downstream.
                        if (correlationId != null) {
                            headers.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    });
            if (body.length > 0) {
                spec.body(body);
            }
            spec.exchange((clientRequest, clientResponse) -> {
                response.setStatus(clientResponse.getStatusCode().value());
                clientResponse.getHeaders().forEach((name, values) -> {
                    if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                        values.forEach(value -> response.addHeader(name, value));
                    }
                });
                clientResponse.getBody().transferTo(response.getOutputStream());
                return null;
            }, false);
        } catch (ResourceAccessException e) {
            log.warn("{} is unavailable: {}", route.serviceName(), e.getMessage());
            writeUnavailable(request, response, route.serviceName());
        }
    }

    private void writeNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errorResponseWriter.write(request, response, HttpServletResponse.SC_NOT_FOUND,
                "ROUTE_NOT_FOUND", "No route configured for this path");
    }

    private void writeUnavailable(HttpServletRequest request, HttpServletResponse response, String serviceName) throws IOException {
        errorResponseWriter.write(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE", serviceName + " is unavailable");
    }
}
