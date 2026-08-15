package com.saga.be.controller;

import com.saga.be.dto.response.GitHubInstallationResponse;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.integration.project.ProjectIntegrationService;
import com.saga.be.integration.callback.IntegrationCallbackRedirectService;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tích hợp dự án", description = "Hoàn tất callback cài đặt GitHub cho dự án.")
public class ProjectIntegrationCallbackController {

    private final ProjectIntegrationService integrationService;
    private final IntegrationAvailability availability;
    private final IntegrationCallbackResultStore resultStore;
    private final IntegrationCallbackRedirectService callbackRedirectService;

    public ProjectIntegrationCallbackController(
            ProjectIntegrationService integrationService,
            IntegrationAvailability availability,
            IntegrationCallbackResultStore resultStore,
            IntegrationCallbackRedirectService callbackRedirectService
    ) {
        this.integrationService = integrationService;
        this.availability = availability;
        this.resultStore = resultStore;
        this.callbackRedirectService = callbackRedirectService;
    }

    @GetMapping("/api/integrations/github/setup")
    public ResponseEntity<Void> githubSetup(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, name = "installation_id")
            Long installationId,
            @RequestParam(required = false, name = "setup_action")
            String setupAction
    ) {
        availability.requireGitHub();
        URI uri = integrationService
                .beginGitHubInstallationVerificationFromProvider(
                        principal,
                        session,
                        state,
                        installationId,
                        setupAction
                );
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

    @GetMapping("/api/integrations/github/project/callback")
    public ResponseEntity<Void> githubCallback(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError
    ) {
        availability.requireGitHub();
        String resultId = resultStore.store(session, principal,
                integrationService.finishGitHubInstallationFromProviderCallback(
                principal,
                session,
                state,
                code,
                oauthError
        ));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(callbackRedirectService.callbackResultUri(resultId))
                .build();
    }
}
