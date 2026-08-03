package com.saga.be.controller;

import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.dto.response.PersonalIntegrationsResponse;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.integration.identity.PersonalIntegrationService;
import com.saga.be.integration.callback.IntegrationCallbackRedirectService;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/integrations")
public class PersonalIntegrationController {

    private final PersonalIntegrationService integrationService;
    private final IntegrationAvailability availability;
    private final IntegrationCallbackResultStore resultStore;
    private final IntegrationCallbackRedirectService callbackRedirectService;

    public PersonalIntegrationController(
            PersonalIntegrationService integrationService,
            IntegrationAvailability availability,
            IntegrationCallbackResultStore resultStore,
            IntegrationCallbackRedirectService callbackRedirectService
    ) {
        this.integrationService = integrationService;
        this.availability = availability;
        this.resultStore = resultStore;
        this.callbackRedirectService = callbackRedirectService;
    }

    @GetMapping
    public PersonalIntegrationsResponse connections(
            @AuthenticationPrincipal SagaPrincipal principal
    ) {
        return integrationService.connections(principal);
    }

    @GetMapping("/jira/connect")
    public ResponseEntity<Void> connectJira(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session
    ) {
        availability.requireJira();
        return redirect(integrationService.beginJira(principal, session));
    }

    @DeleteMapping("/jira")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectJira(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpServletRequest request
    ) {
        integrationService.disconnect(
                principal,
                IntegrationProvider.JIRA,
                request.getRemoteAddr()
        );
    }

    @GetMapping("/github/connect")
    public ResponseEntity<Void> connectGitHub(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session
    ) {
        availability.requireGitHub();
        return redirect(integrationService.beginGitHub(principal, session));
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError,
            HttpServletRequest request
    ) {
        availability.requireGitHub();
        String resultId = resultStore.store(session, principal,
                integrationService.finishGitHubCallback(
                principal,
                session,
                state,
                code,
                oauthError,
                request.getRemoteAddr()
        ));
        return redirect(callbackRedirectService.callbackResultUri(resultId));
    }

    @DeleteMapping("/github")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectGitHub(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpServletRequest request
    ) {
        integrationService.disconnect(
                principal,
                IntegrationProvider.GITHUB,
                request.getRemoteAddr()
        );
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
