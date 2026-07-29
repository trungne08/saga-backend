package com.saga.be.controller;

import com.saga.be.integration.callback.JiraOAuthCallbackService;
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

    public JiraIntegrationCallbackController(
            JiraOAuthCallbackService callbackService
    ) {
        this.callbackService = callbackService;
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
