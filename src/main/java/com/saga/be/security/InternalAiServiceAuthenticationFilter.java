package com.saga.be.security;

import com.saga.be.config.InternalAiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalAiServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-SAGA-AI-Service-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/ai/";
    private static final int MINIMUM_TOKEN_LENGTH = 32;

    private final InternalAiProperties properties;
    private final SecurityErrorResponseWriter responseWriter;

    public InternalAiServiceAuthenticationFilter(
            InternalAiProperties properties,
            SecurityErrorResponseWriter responseWriter
    ) {
        this.properties = properties;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String configured = properties.serviceToken();
        if (configured == null || configured.length() < MINIMUM_TOKEN_LENGTH) {
            responseWriter.write(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "INTERNAL_SERVICE_AUTH_NOT_CONFIGURED",
                    "Internal service authentication is unavailable"
            );
            return;
        }
        String presented = request.getHeader(HEADER_NAME);
        if (presented == null || !constantTimeEquals(configured, presented)) {
            responseWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INTERNAL_SERVICE_AUTHENTICATION_REQUIRED",
                    "Valid internal service authentication is required"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String configured, String presented) {
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8)
        );
    }
}
