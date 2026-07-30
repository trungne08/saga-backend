package com.saga.be.controller;

import com.saga.be.dto.response.GitHubInstallationResponse;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.integration.project.ProjectIntegrationService;
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
public class ProjectIntegrationCallbackController {

    private final ProjectIntegrationService integrationService;
    private final IntegrationAvailability availability;

    public ProjectIntegrationCallbackController(
            ProjectIntegrationService integrationService,
            IntegrationAvailability availability
    ) {
        this.integrationService = integrationService;
        this.availability = availability;
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
    public GitHubInstallationResponse githubCallback(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError
    ) {
        availability.requireGitHub();
        return integrationService.finishGitHubInstallationFromProvider(
                principal,
                session,
                state,
                code,
                oauthError
        );
    }
}
