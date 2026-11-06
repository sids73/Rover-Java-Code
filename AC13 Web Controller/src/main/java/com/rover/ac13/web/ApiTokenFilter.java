package com.rover.ac13.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;

/**
 * Requires {@code Authorization: Bearer <token>} on mutating {@code /api/**} requests
 * when {@code rover.api.token} is set. Read-only requests (GET/HEAD/OPTIONS) and
 * everything outside {@code /api/} (UI, actuator) stay public.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] expectedToken;
    private final JsonMapper jsonMapper;

    public ApiTokenFilter(@Value("${rover.api.token:}") String token, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        if (token == null || token.isBlank()) {
            this.expectedToken = null;
            LOG.warn("rover.api.token is not set: mutating /api endpoints are NOT protected");
        } else {
            this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken == null
                || SAFE_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (hasValidToken(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Missing or invalid API token",
                request.getRequestURI(),
                (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), error);
    }

    private boolean hasValidToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] supplied = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }
}
