package com.saga.be.controller;

import com.saga.be.integration.callback.JiraOAuthCallbackService;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JiraIntegrationCallbackController {

    private final JiraOAuthCallbackService callbackService;
    private final IntegrationAvailability availability;

    public JiraIntegrationCallbackController(
            JiraOAuthCallbackService callbackService,
            IntegrationAvailability availability
    ) {
        this.callbackService = callbackService;
        this.availability = availability;
    }

    @GetMapping("/api/integrations/jira/callback")
    public Object callback(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError,
            HttpServletRequest request
    ) {
        availability.requireJira();
        return callbackService.complete(
                principal,
                session,
                state,
                code,
                oauthError,
                request.getRemoteAddr()
        );
    }
}
