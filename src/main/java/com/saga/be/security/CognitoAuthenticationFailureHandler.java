package com.saga.be.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CognitoAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            CognitoAuthenticationFailureHandler.class
    );

    private final SecurityErrorResponseWriter responseWriter;

    public CognitoAuthenticationFailureHandler(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // Do not expose provider details to the browser, but retain the reason in
        // the server log so OAuth2 token-exchange failures can be diagnosed.
        LOGGER.warn(
                "Cognito OAuth2 callback failed: type={}, message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        responseWriter.write(
                request,
                response,
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "Cognito authentication could not be completed"
        );
    }
}
