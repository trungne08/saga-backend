package com.saga.be.security;

import com.saga.be.service.AgentAiSafeErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    public JsonAuthenticationEntryPoint(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        boolean agentChat = AgentAiSafeErrors.isPublicAgentPath(request.getRequestURI());
        responseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                AgentAiSafeErrors.AUTHENTICATION_REQUIRED_CODE,
                agentChat ? AgentAiSafeErrors.SESSION_EXPIRED_MESSAGE : "Authentication is required"
        );
    }
}
