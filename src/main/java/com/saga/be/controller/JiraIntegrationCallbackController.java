package com.saga.be.controller;

import com.saga.be.integration.callback.JiraOAuthCallbackService;
import com.saga.be.integration.callback.IntegrationCallbackRedirectService;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đồng bộ dữ liệu", description = "Hoàn tất callback OAuth Jira.")
public class JiraIntegrationCallbackController {

    private final JiraOAuthCallbackService callbackService;
    private final IntegrationAvailability availability;
    private final IntegrationCallbackResultStore resultStore;
    private final IntegrationCallbackRedirectService redirectService;

    public JiraIntegrationCallbackController(
            JiraOAuthCallbackService callbackService,
            IntegrationAvailability availability,
            IntegrationCallbackResultStore resultStore,
            IntegrationCallbackRedirectService redirectService
    ) {
        this.callbackService = callbackService;
        this.availability = availability;
        this.resultStore = resultStore;
        this.redirectService = redirectService;
    }

    @GetMapping("/api/integrations/jira/callback")
    public org.springframework.http.ResponseEntity<Void> callback(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError,
            HttpServletRequest request
    ) {
        availability.requireJira();
        String resultId = resultStore.store(session, principal, callbackService.complete(
                principal,
                session,
                state,
                code,
                oauthError,
                request.getRemoteAddr()
        ));
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.FOUND)
                .location(redirectService.callbackResultUri(resultId))
                .build();
    }
}
