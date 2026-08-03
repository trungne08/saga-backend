package com.saga.be.controller;

import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntegrationCallbackResultController {

    private final IntegrationCallbackResultStore resultStore;
    private final ProjectIntegrationAuthorizationService authorization;

    public IntegrationCallbackResultController(
            IntegrationCallbackResultStore resultStore,
            ProjectIntegrationAuthorizationService authorization
    ) {
        this.resultStore = resultStore;
        this.authorization = authorization;
    }

    @PostMapping("/api/integrations/callback-results/{resultId}/consume")
    public IntegrationCallbackResultResponse consume(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @PathVariable String resultId
    ) {
        return resultStore.consume(session, principal, resultId, result ->
                authorization.requireProjectManager(principal, result.projectId())
        );
    }
}
