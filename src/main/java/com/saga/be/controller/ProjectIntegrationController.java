package com.saga.be.controller;

import com.saga.be.dto.request.GitHubRepositoriesLinkRequest;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.dto.request.JiraProjectLinkRequest;
import com.saga.be.dto.request.ManualSyncProvider;
import com.saga.be.dto.response.GitHubInstallationResponse;
import com.saga.be.dto.response.JiraAuthorizationResponse;
import com.saga.be.dto.response.ProjectIntegrationsResponse;
import com.saga.be.dto.response.SyncStatusResponse;
import com.saga.be.dto.response.SyncHistoryResponse;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.dto.response.ManualProjectSyncResponse;
import com.saga.be.integration.project.ProjectIntegrationService;
import com.saga.be.integration.project.ManualProjectSyncService;
import com.saga.be.integration.callback.IntegrationCallbackRedirectService;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tích hợp dự án", description = "Liên kết, trạng thái và đồng bộ Jira/GitHub của dự án.")
@RequestMapping("/api/projects/{projectId}")
public class ProjectIntegrationController {

    private final ProjectIntegrationService integrationService;
    private final ManualProjectSyncService manualSyncService;
    private final IntegrationAvailability availability;
    private final IntegrationCallbackResultStore resultStore;
    private final IntegrationCallbackRedirectService callbackRedirectService;

    public ProjectIntegrationController(
            ProjectIntegrationService integrationService,
            ManualProjectSyncService manualSyncService,
            IntegrationAvailability availability,
            IntegrationCallbackResultStore resultStore,
            IntegrationCallbackRedirectService callbackRedirectService
    ) {
        this.integrationService = integrationService;
        this.manualSyncService = manualSyncService;
        this.availability = availability;
        this.resultStore = resultStore;
        this.callbackRedirectService = callbackRedirectService;
    }

    @GetMapping("/integrations")
    public ProjectIntegrationsResponse integrations(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return integrationService.integrations(principal, projectId);
    }

    @GetMapping("/jira/connect")
    public ResponseEntity<Void> jiraConnect(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpSession session
    ) {
        availability.requireJira();
        return redirect(integrationService.beginJira(
                principal,
                projectId,
                session
        ));
    }

    @PostMapping("/jira/link")
    public ProjectIntegrationsResponse jiraLink(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpSession session,
            @Valid @RequestBody JiraProjectLinkRequest request,
            HttpServletRequest servletRequest
    ) {
        return integrationService.linkJira(
                principal,
                projectId,
                session,
                request,
                servletRequest.getRemoteAddr()
        );
    }

    @DeleteMapping("/jira")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void jiraDisconnect(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpServletRequest request
    ) {
        integrationService.disconnectJira(
                principal,
                projectId,
                request.getRemoteAddr()
        );
    }

    @GetMapping("/github/install")
    public ResponseEntity<Void> githubInstall(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpSession session
    ) {
        availability.requireGitHub();
        return redirect(integrationService.beginGitHubInstallation(
                principal,
                projectId,
                session
        ));
    }

    @GetMapping("/github/setup")
    public ResponseEntity<Void> githubSetup(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, name = "installation_id")
            Long installationId,
            @RequestParam(required = false, name = "setup_action")
            String setupAction
    ) {
        return redirect(
                integrationService.beginGitHubInstallationVerification(
                        principal,
                        projectId,
                        session,
                        state,
                        installationId,
                        setupAction
                )
        );
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            HttpSession session,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError
    ) {
        availability.requireGitHub();
        String resultId = resultStore.store(session, principal,
                integrationService.finishGitHubInstallationCallback(
                principal,
                projectId,
                session,
                state,
                code,
                oauthError
        ));
        return redirect(callbackRedirectService.callbackResultUri(resultId));
    }

    @PostMapping("/github/repositories")
    public ProjectIntegrationsResponse githubRepositories(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody GitHubRepositoriesLinkRequest request,
            HttpServletRequest servletRequest
    ) {
        return integrationService.linkGitHubRepositories(
                principal,
                projectId,
                request,
                servletRequest.getRemoteAddr()
        );
    }

    @DeleteMapping("/github/repositories/{repositoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void githubRepositoryDisconnect(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable Long repositoryId,
            HttpServletRequest request
    ) {
        integrationService.disconnectGitHubRepository(
                principal,
                projectId,
                repositoryId,
                request.getRemoteAddr()
        );
    }

    @GetMapping("/sync-status")
    public SyncStatusResponse syncStatus(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return integrationService.syncStatus(principal, projectId);
    }

    @GetMapping("/sync-history")
    public SyncHistoryResponse syncHistory(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String targetSystem,
            @RequestParam(required = false) SyncJobStatus status,
            @RequestParam(required = false) SyncJobType jobType
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw com.saga.be.exception.IntegrationException.invalid(
                    "SYNC_HISTORY_PAGE_INVALID", "Pagination is invalid");
        }
        return integrationService.syncHistory(
                principal, projectId, page, size, targetSystem, status, jobType);
    }

    @PostMapping("/github/repositories/{repositoryId}/connect")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void githubRepositoryReconnect(@AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId, @PathVariable Long repositoryId, HttpServletRequest request) {
        integrationService.reconnectGitHubRepository(principal, projectId, repositoryId, request.getRemoteAddr());
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ManualProjectSyncResponse sync(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "ALL") ManualSyncProvider provider
    ) {
        return manualSyncService.request(principal, projectId, provider);
    }

    private ResponseEntity<Void> redirect(URI uri) {
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }
}
