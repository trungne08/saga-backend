package com.saga.be.integration.project;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationUrlResolver;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.dto.request.GitHubRepositoriesLinkRequest;
import com.saga.be.dto.request.JiraProjectLinkRequest;
import com.saga.be.dto.response.GitHubInstallationResponse;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.dto.response.GitHubRepositoryResponse;
import com.saga.be.dto.response.JiraAuthorizationResponse;
import com.saga.be.dto.response.JiraSiteResponse;
import com.saga.be.dto.response.ProjectIntegrationsResponse;
import com.saga.be.dto.response.SyncStatusResponse;
import com.saga.be.dto.response.SyncHistoryResponse;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubInstallationInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubRepositoryInfo;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.provider.JiraProjectInfo;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWebhookRegistration;
import com.saga.be.integration.provider.JiraWriteScope;
import com.saga.be.integration.security.IntegrationAttemptLimiter;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.integration.security.OAuthFlow;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SyncJobLogRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProjectIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(
            ProjectIntegrationService.class
    );
    private final ProjectIntegrationAuthorizationService authorization;
    private final OAuthStateService stateService;
    private final ProjectIntegrationSessionStore sessionStore;
    private final JiraProviderClient jiraClient;
    private final GitHubProviderClient gitHubClient;
    private final IntegrationAvailability availability;
    private final JiraIntegrationProperties jiraProperties;
    private final GitHubIntegrationProperties gitHubProperties;
    private final IntegrationUrlResolver urlResolver;
    private final IntegrationSecretCipher cipher;
    private final JiraCredentialService jiraCredentialService;
    private final JiraBoardResolutionService jiraBoardResolutionService;
    private final JiraBoardLinkPersistenceService jiraBoardLinkPersistenceService;
    private final JiraBoardRepository jiraBoardRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final SyncJobLogRepository syncJobLogRepository;
    private final StudentRepository studentRepository;
    private final AutomaticSyncDispatcher syncDispatcher;
    private final ApplicationEventPublisher eventPublisher;
    private final IntegrationAttemptLimiter attemptLimiter;
    private final AuthenticationAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProjectIntegrationService(
            ProjectIntegrationAuthorizationService authorization,
            OAuthStateService stateService,
            ProjectIntegrationSessionStore sessionStore,
            JiraProviderClient jiraClient,
            GitHubProviderClient gitHubClient,
            IntegrationAvailability availability,
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties,
            IntegrationUrlResolver urlResolver,
            IntegrationSecretCipher cipher,
            JiraCredentialService jiraCredentialService,
            JiraBoardResolutionService jiraBoardResolutionService,
            JiraBoardLinkPersistenceService jiraBoardLinkPersistenceService,
            JiraBoardRepository jiraBoardRepository,
            GitHubInstallationRepository installationRepository,
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository syncJobLogRepository,
            StudentRepository studentRepository,
            AutomaticSyncDispatcher syncDispatcher,
            ApplicationEventPublisher eventPublisher,
            IntegrationAttemptLimiter attemptLimiter,
            AuthenticationAuditService auditService
    ) {
        this.authorization = authorization;
        this.stateService = stateService;
        this.sessionStore = sessionStore;
        this.jiraClient = jiraClient;
        this.gitHubClient = gitHubClient;
        this.availability = availability;
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
        this.urlResolver = urlResolver;
        this.cipher = cipher;
        this.jiraCredentialService = jiraCredentialService;
        this.jiraBoardResolutionService = jiraBoardResolutionService;
        this.jiraBoardLinkPersistenceService = jiraBoardLinkPersistenceService;
        this.jiraBoardRepository = jiraBoardRepository;
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.syncJobLogRepository = syncJobLogRepository;
        this.studentRepository = studentRepository;
        this.syncDispatcher = syncDispatcher;
        this.eventPublisher = eventPublisher;
        this.attemptLimiter = attemptLimiter;
        this.auditService = auditService;
    }

    public ProjectIntegrationsResponse integrations(
            SagaPrincipal principal,
            UUID projectId
    ) {
        authorization.requireProjectManager(principal, projectId);
        JiraBoard board = jiraBoardRepository.findByProjectId(projectId)
                .orElse(null);
        var jira = board == null
                ? null
                : new ProjectIntegrationsResponse.JiraProjectIntegration(
                        board.getSiteUrl(),
                        board.getProjectKey(),
                        board.getConnectionStatus(),
                        board.getWebhookExpiresAt(),
                        board.getLastSyncedAt()
                );
        return new ProjectIntegrationsResponse(
                projectId,
                jira,
                gitRepoRepository.findByProjectIdOrderByFullName(projectId)
                        .stream()
                        .map(GitHubRepositoryResponse::from)
                        .toList()
        );
    }

    public URI beginJira(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-jira-connect");
        requireConfiguredJiraScopes();
        String state = stateService.issue(
                session,
                principal,
                OAuthFlow.PROJECT_JIRA,
                projectId
        );
        return jiraClient.authorizationUri(
                state,
                jiraCallbackUrl()
        );
    }

    public JiraAuthorizationResponse completeJiraCallback(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String code,
            String oauthError
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-jira-callback");
        return finishJiraAuthorization(projectId, session, code, oauthError);
    }

    private JiraAuthorizationResponse finishJiraAuthorization(
            UUID projectId,
            HttpSession session,
            String code,
            String oauthError
    ) {
        requireConsent(code, oauthError);

        JiraOAuthToken token = jiraClient.exchangeCode(
                code,
                jiraCallbackUrl()
        );
        List<JiraAccessibleResource> resources =
                jiraClient.accessibleResources(token.accessToken());
        if (resources.isEmpty()) {
            throw IntegrationException.conflict(
                    "JIRA_SITE_ACCESS_MISSING",
                    "No accessible Jira Cloud site was returned"
            );
        }
        sessionStore.putJiraGrant(session, projectId, token, resources);
        return new JiraAuthorizationResponse(
                projectId,
                resources.stream().map(JiraSiteResponse::from).toList()
        );
    }

    public ProjectIntegrationsResponse linkJira(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            JiraProjectLinkRequest request,
            String remoteAddress
    ) {
        Project project = authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-jira-link");
        JiraLinkStage stage = JiraLinkStage.LOAD_FRESH_GRANT;
        String verifiedCloudId = null;
        Set<String> missingScopeNames = Set.of();
        try {
            ProjectIntegrationSessionStore.ResolvedJiraGrant grant =
                    sessionStore.requireJiraGrant(session, projectId);
            stage = JiraLinkStage.VERIFY_ACCESSIBLE_RESOURCE;
            JiraAccessibleResource resource = grant.resources().stream()
                    .filter(value -> value.cloudId().equals(request.cloudId()))
                    .findFirst()
                    .orElseThrow(() -> IntegrationException.conflict(
                            "JIRA_SITE_NOT_AUTHORIZED",
                            "The selected Jira site was not authorized in this session"
                    ));
            verifiedCloudId = resource.cloudId();
            stage = JiraLinkStage.SCOPE_PREFLIGHT;
            missingScopeNames = JiraWriteScope.missing(
                    resource.scopes(),
                    JiraWriteScope.linkScopes().toArray(String[]::new)
            );
            requireJiraLinkScopes(resource.scopes());
            stage = JiraLinkStage.RESOLVE_PROJECT;
            JiraProjectInfo jiraProject = jiraClient.projects(
                        grant.accessToken(),
                        resource.cloudId()
                    )
                    .stream()
                    .filter(value -> matchesJiraProject(
                            value,
                            request.jiraProjectId()
                    ))
                    .findFirst()
                    .orElseThrow(() -> IntegrationException.conflict(
                            "JIRA_PROJECT_NOT_ACCESSIBLE",
                            "The selected Jira project is not accessible"
                    ));

            JiraBoard discovery = JiraBoard.builder()
                    .project(project)
                    .name(jiraProject.name())
                    .cloudId(resource.cloudId())
                    .siteUrl(resource.siteUrl())
                    .jiraProjectId(jiraProject.id())
                    .projectKey(jiraProject.key())
                    .connectionStatus(IntegrationStatus.CONNECTING)
                    .build();
            // Provider I/O occurs before the short, locked local upsert transaction.
            stage = JiraLinkStage.DISCOVER_SCRUM_BOARDS;
            jiraBoardResolutionService.resolveForLinking(discovery, grant.accessToken());
            JiraBoardLinkCommand command = new JiraBoardLinkCommand(
                    project,
                    jiraProject.name(),
                    resource.cloudId(),
                    resource.siteUrl(),
                    jiraProject.id(),
                    jiraProject.key(),
                    discovery.getJiraBoardId(),
                    grant.accessToken(),
                    grant.refreshToken(),
                    grant.tokenExpiresAt(),
                    resource.scopes(),
                    principal.cognitoSub(),
                    principal.applicationRole() == ApplicationRole.STUDENT
                            ? projectStudent(principal)
                            : null
            );
            stage = JiraLinkStage.UPSERT_JIRA_BOARD;
            JiraBoard board = upsertJiraBoard(command, projectId);

            String webhookSecret = randomSecret();
            URI callback = jiraWebhookCallback(webhookSecret);
            JiraWebhookRegistration registration = null;
            try {
                stage = JiraLinkStage.REGISTER_WEBHOOK;
            registration = jiraClient.ensureWebhook(
                    grant.accessToken(),
                    resource.cloudId(),
                    jiraProject.key(),
                    callback,
                    board.getWebhookId()
            );
            JiraBoard saved = jiraBoardLinkPersistenceService.complete(
                    command,
                    registration.webhookId(),
                    registration.created(),
                    registration.created() ? sha256(webhookSecret) : null
            );
            sessionStore.removeJiraGrant(session, projectId);

            dispatchAfterCommit(() -> syncDispatcher.initialJiraBackfill(saved.getId()));
            auditService.recordIntegrationEvent(
                    principal.cognitoSub(),
                    "PROJECT_JIRA_LINKED",
                    "PROJECT",
                    projectId,
                    "BACKFILLING",
                    remoteAddress
            );
                return integrations(principal, projectId);
            } catch (RuntimeException exception) {
                compensateCreatedWebhook(
                        registration,
                        grant.accessToken(),
                        resource.cloudId(),
                        projectId
                );
                throw exception;
            }
        } catch (IntegrationException exception) {
            logJiraLinkFailure(
                    projectId, stage, verifiedCloudId, missingScopeNames, exception
            );
            throw exception;
        }
    }

    private boolean matchesJiraProject(
            JiraProjectInfo jiraProject,
            String requestedProjectIdOrKey
    ) {
        String requested = requestedProjectIdOrKey == null
                ? ""
                : requestedProjectIdOrKey.trim();
        return jiraProject.id().equals(requested)
                || jiraProject.key().equalsIgnoreCase(requested);
    }

    @Transactional
    public void disconnectJira(
            SagaPrincipal principal,
            UUID projectId,
            String remoteAddress
    ) {
        authorization.requireProjectManager(principal, projectId);
        JiraBoard board = jiraBoardRepository.findByProjectId(projectId)
                .orElse(null);
        if (board == null || board.getConnectionStatus()
                == IntegrationStatus.DISCONNECTED) {
            return;
        }
        try {
            if (board.getWebhookId() != null) {
                jiraClient.deleteWebhook(
                        jiraCredentialService.validAccessToken(board),
                        board.getCloudId(),
                        board.getWebhookId()
                );
            }
        } catch (IntegrationException ignored) {
            // Local disconnection remains authoritative; the dynamic webhook
            // expires and its per-connection secret is invalidated below.
        }
        board.setConnectionStatus(IntegrationStatus.DISCONNECTED);
        board.setEncryptedAccessToken(null);
        board.setEncryptedRefreshToken(null);
        board.setTokenExpiresAt(null);
        board.setGrantedScopes(null);
        board.setWebhookId(null);
        board.setWebhookSecretHash(null);
        board.setWebhookExpiresAt(null);
        jiraBoardRepository.saveAndFlush(board);
        auditService.recordIntegrationEvent(
                principal.cognitoSub(),
                "PROJECT_JIRA_DISCONNECTED",
                "PROJECT",
                projectId,
                "SUCCESS",
                remoteAddress
        );
    }

    public URI beginGitHubInstallation(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-install");
        String state = stateService.issue(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION,
                projectId
        );
        return gitHubClient.installationUri(state);
    }

    public URI beginGitHubInstallationVerification(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String state,
            Long installationId,
            String setupAction
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-setup");
        stateService.consume(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION,
                projectId,
                state
        );
        return startGitHubInstallationVerification(
                principal,
                projectId,
                session,
                installationId,
                setupAction
        );
    }

    public URI beginGitHubInstallationVerificationFromProvider(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            Long installationId,
            String setupAction
    ) {
        OAuthStateService.StateBinding binding =
                stateService.consumeWithResolvedTarget(
                        session,
                        principal,
                        OAuthFlow.PROJECT_GITHUB_INSTALLATION,
                        state
                );
        UUID projectId = binding.targetId();
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-setup");
        return startGitHubInstallationVerification(
                principal,
                projectId,
                session,
                installationId,
                setupAction
        );
    }

    public GitHubInstallationResponse finishGitHubInstallation(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String state,
            String code,
            String oauthError
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-verify");
        stateService.consume(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION_VERIFY,
                projectId,
                state
        );
        return completeGitHubInstallation(
                principal,
                projectId,
                session,
                code,
                oauthError
        );
    }

    public IntegrationCallbackResultResponse finishGitHubInstallationCallback(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String state,
            String code,
            String oauthError
    ) {
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-verify");
        stateService.consume(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION_VERIFY,
                projectId,
                state
        );
        return completeGitHubInstallationResult(
                principal,
                projectId,
                session,
                code,
                oauthError
        );
    }

    public GitHubInstallationResponse finishGitHubInstallationFromProvider(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            String code,
            String oauthError
    ) {
        OAuthStateService.StateBinding binding =
                stateService.consumeWithResolvedTarget(
                        session,
                        principal,
                        OAuthFlow.PROJECT_GITHUB_INSTALLATION_VERIFY,
                        state
                );
        UUID projectId = binding.targetId();
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-verify");
        return completeGitHubInstallation(
                principal,
                projectId,
                session,
                code,
                oauthError
        );
    }

    public IntegrationCallbackResultResponse
            finishGitHubInstallationFromProviderCallback(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            String code,
            String oauthError
    ) {
        OAuthStateService.StateBinding binding =
                stateService.consumeWithResolvedTarget(
                        session,
                        principal,
                        OAuthFlow.PROJECT_GITHUB_INSTALLATION_VERIFY,
                        state
                );
        UUID projectId = binding.targetId();
        authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-verify");
        return completeGitHubInstallationResult(
                principal,
                projectId,
                session,
                code,
                oauthError
        );
    }

    private IntegrationCallbackResultResponse completeGitHubInstallationResult(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String code,
            String oauthError
    ) {
        try {
            return IntegrationCallbackResultResponse.projectGitHubSuccess(
                    completeGitHubInstallation(
                            principal,
                            projectId,
                            session,
                            code,
                            oauthError
                    )
            );
        } catch (IntegrationException exception) {
            return IntegrationCallbackResultResponse.failure(
                    com.saga.be.entity.enums.IntegrationProvider.GITHUB,
                    com.saga.be.dto.response.IntegrationCallbackFlow.PROJECT,
                    projectId,
                    exception.getCode(),
                    exception.getMessage()
            );
        }
    }

    private URI startGitHubInstallationVerification(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            Long installationId,
            String setupAction
    ) {
        if (
            installationId == null
            || installationId <= 0
            || "request".equalsIgnoreCase(setupAction)
        ) {
            throw IntegrationException.invalid(
                    "GITHUB_INSTALLATION_INCOMPLETE",
                    "GitHub App installation was not completed"
            );
        }
        sessionStore.putGitHubInstallation(
                session,
                projectId,
                installationId
        );
        String verificationState = stateService.issue(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION_VERIFY,
                projectId
        );
        return gitHubClient.userAuthorizationUri(
                verificationState,
                gitHubProperties.projectCallbackUrl()
        );
    }

    private GitHubInstallationResponse completeGitHubInstallation(
            SagaPrincipal principal,
            UUID projectId,
            HttpSession session,
            String code,
            String oauthError
    ) {
        requireConsent(code, oauthError);
        long installationId = sessionStore.requireGitHubInstallation(
                session,
                projectId
        );
        String userToken = gitHubClient.exchangeUserCode(
                code,
                gitHubProperties.projectCallbackUrl()
        );
        if (!gitHubClient.userCanAccessInstallation(
                userToken,
                installationId
        )) {
            sessionStore.removeGitHubInstallation(session, projectId);
            throw IntegrationException.forbidden(
                    "The authenticated GitHub user cannot access this installation"
            );
        }
        sessionStore.removeGitHubInstallation(session, projectId);

        GitHubInstallationInfo info = gitHubClient.installation(installationId);
        if (info.suspended()) {
            throw IntegrationException.conflict(
                    "GITHUB_INSTALLATION_SUSPENDED",
                    "The GitHub App installation is suspended"
            );
        }
        GitHubInstallation installation = installationRepository
                .findByInstallationId(installationId)
                .orElseGet(() -> GitHubInstallation.builder()
                        .installationId(installationId)
                        .build());
        installation.setInstalledByCognitoSub(principal.cognitoSub());
        installation.setInstalledByStudent(
                principal.applicationRole() == ApplicationRole.STUDENT
                        ? projectStudent(principal)
                        : null
        );
        installation.setAccountLogin(info.accountLogin());
        installation.setAccountType(info.accountType());
        installation.setInstallationStatus(GitHubInstallationStatus.ACTIVE);
        installation.setLastVerifiedAt(LocalDateTime.now());
        installation.setConsecutiveFailures(0);
        installationRepository.saveAndFlush(installation);
        List<GitHubRepositoryInfo> repositories =
                gitHubClient.installationRepositories(installationId);
        return GitHubInstallationResponse.from(projectId, info, repositories);
    }

    @Transactional
    public ProjectIntegrationsResponse linkGitHubRepositories(
            SagaPrincipal principal,
            UUID projectId,
            GitHubRepositoriesLinkRequest request,
            String remoteAddress
    ) {
        availability.requireGitHub();
        Project project = authorization.requireProjectManager(principal, projectId);
        limit(principal, "project-github-link");
        GitHubInstallation installation = installationRepository
                .findByInstallationId(request.installationId())
                .orElseThrow(() -> IntegrationException.conflict(
                        "GITHUB_INSTALLATION_NOT_FOUND",
                        "Complete GitHub App installation before linking repositories"
                ));
        requireInstallationOwner(principal, installation);
        if (installation.getInstallationStatus()
                != GitHubInstallationStatus.ACTIVE) {
            throw IntegrationException.conflict(
                    "GITHUB_INSTALLATION_INACTIVE",
                    "The GitHub App installation is not active"
            );
        }

        Set<Long> requestedIds = new HashSet<>(request.repositoryIds());
        if (requestedIds.size() != request.repositoryIds().size()) {
            throw IntegrationException.invalid(
                    "GITHUB_REPOSITORY_DUPLICATE",
                    "Repository selections must be unique"
            );
        }
        Map<Long, GitHubRepositoryInfo> available = gitHubClient
                .installationRepositories(request.installationId())
                .stream()
                .collect(Collectors.toMap(
                        GitHubRepositoryInfo::id,
                        Function.identity()
                ));
        if (!available.keySet().containsAll(requestedIds)) {
            throw IntegrationException.conflict(
                    "GITHUB_REPOSITORY_NOT_ACCESSIBLE",
                    "One or more selected repositories are not available to the installation"
            );
        }

        for (Long repositoryId : requestedIds) {
            GitHubRepositoryInfo info = available.get(repositoryId);
            GitRepo repository = gitRepoRepository
                    .findByRepositoryId(repositoryId)
                    .orElseGet(() -> GitRepo.builder()
                            .project(project)
                            .provider("GITHUB")
                            .repositoryId(repositoryId)
                            .build());
            if (
                repository.getProject() != null
                && !repository.getProject().getId().equals(projectId)
            ) {
                throw IntegrationException.conflict(
                        "GITHUB_REPOSITORY_ALREADY_LINKED",
                        "A selected repository is already linked to another SAGA Project"
                );
            }
            repository.setProject(project);
            repository.setInstallation(installation);
            repository.setOwnerLogin(info.owner());
            repository.setName(info.name());
            repository.setFullName(info.fullName());
            repository.setUrl(info.htmlUrl());
            repository.setDefaultBranch(info.defaultBranch());
            boolean alreadySynced = repository.getConnectionStatus()
                    == IntegrationStatus.ACTIVE
                    && repository.getLastSyncedAt() != null;
            if (!alreadySynced) {
                repository.setConnectionStatus(IntegrationStatus.BACKFILLING);
                if (repository.getId() == null) {
                    repository.setConsecutiveFailures(0);
                }
            }
            GitRepo saved = gitRepoRepository.saveAndFlush(repository);
            if (!alreadySynced) {
                eventPublisher.publishEvent(
                        new GitHubInitialBackfillRequested(saved.getId())
                );
            }
        }

        auditService.recordIntegrationEvent(
                principal.cognitoSub(),
                "PROJECT_GITHUB_REPOSITORIES_LINKED",
                "PROJECT",
                projectId,
                "BACKFILLING",
                remoteAddress
        );
        return integrations(principal, projectId);
    }

    public void disconnectGitHubRepository(
            SagaPrincipal principal,
            UUID projectId,
            Long repositoryId,
            String remoteAddress
    ) {
        authorization.requireProjectManager(principal, projectId);
        GitRepo repository = gitRepoRepository
                .findByProjectIdAndRepositoryId(projectId, repositoryId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "GITHUB_REPOSITORY_NOT_FOUND",
                        "The linked GitHub repository does not exist"
                ));
        repository.setConnectionStatus(IntegrationStatus.DISCONNECTED);
        gitRepoRepository.saveAndFlush(repository);
        auditService.recordIntegrationEvent(
                principal.cognitoSub(),
                "PROJECT_GITHUB_REPOSITORY_DISCONNECTED",
                "PROJECT",
                projectId,
                "SUCCESS",
                remoteAddress
        );
    }

    @Transactional
    public void reconnectGitHubRepository(SagaPrincipal principal, UUID projectId, Long repositoryId, String remoteAddress) {
        authorization.requireProjectManager(principal, projectId);
        GitRepo repository = gitRepoRepository.findForReconnectByProjectIdAndRepositoryId(projectId, repositoryId)
                .orElseThrow(() -> new IntegrationException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "GITHUB_REPOSITORY_NOT_FOUND", "The linked GitHub repository does not exist"));
        if (repository.getConnectionStatus() != IntegrationStatus.DISCONNECTED) {
            throw IntegrationException.conflict("GITHUB_RECONNECT_NOT_REQUIRED", "The GitHub repository is already connected");
        }
        if (repository.getInstallation() == null || !gitHubClient.installationRepositories(
                repository.getInstallation().getInstallationId()).stream().anyMatch(value ->
                        java.util.Objects.equals(value.id(), repository.getRepositoryId()))) {
            throw IntegrationException.conflict("GITHUB_RECONNECT_REQUIRES_INSTALLATION",
                    "Reconnect the GitHub installation before reconnecting this repository");
        }
        repository.setConnectionStatus(IntegrationStatus.BACKFILLING);
        repository.setConsecutiveFailures(0);
        GitRepo saved = gitRepoRepository.saveAndFlush(repository);
        dispatchAfterCommit(() -> syncDispatcher.initialGitHubBackfill(saved.getId()));
        auditService.recordIntegrationEvent(principal.cognitoSub(), "PROJECT_GITHUB_REPOSITORY_RECONNECTED",
                "PROJECT", projectId, "BACKFILLING", remoteAddress);
    }

    public SyncStatusResponse syncStatus(
            SagaPrincipal principal,
            UUID projectId
    ) {
        authorization.requireProjectManager(principal, projectId);
        Set<UUID> targetIds = syncTargetIds(projectId);
        return new SyncStatusResponse(
                projectId,
                syncJobLogRepository
                        .findTop20ByTargetIdInOrderByStartedAtDesc(
                                targetIds
                        )
                        .stream()
                        .map(SyncStatusResponse.Job::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public SyncHistoryResponse syncHistory(
            SagaPrincipal principal,
            UUID projectId,
            int page,
            int size,
            String targetSystem,
            com.saga.be.entity.enums.SyncJobStatus status,
            com.saga.be.entity.enums.SyncJobType jobType
    ) {
        authorization.requireProjectManager(principal, projectId);
        String normalizedTargetSystem = targetSystem == null || targetSystem.isBlank()
                ? null : targetSystem.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedTargetSystem != null && !normalizedTargetSystem.equals("JIRA")
                && !normalizedTargetSystem.equals("GITHUB")) {
            throw IntegrationException.invalid("SYNC_HISTORY_TARGET_INVALID",
                    "targetSystem must be JIRA or GITHUB");
        }
        return SyncHistoryResponse.from(projectId, syncJobLogRepository.findHistoryByTargetIds(
                syncTargetIds(projectId), normalizedTargetSystem, status, jobType,
                PageRequest.of(page, size)
        ));
    }

    private Set<UUID> syncTargetIds(UUID projectId) {
        Set<UUID> targetIds = gitRepoRepository
                .findByProjectIdOrderByFullName(projectId)
                .stream()
                .map(GitRepo::getId)
                .collect(Collectors.toSet());
        targetIds.add(projectId);
        jiraBoardRepository.findByProjectId(projectId)
                .map(JiraBoard::getId)
                .ifPresent(targetIds::add);
        return targetIds;
    }

    private void requireInstallationOwner(
            SagaPrincipal principal,
            GitHubInstallation installation
    ) {
        if (
            principal.applicationRole() == ApplicationRole.STUDENT
            && (
                installation.getInstalledByStudent() == null
                || !principal.localProfileId().equals(
                        installation.getInstalledByStudent().getId()
                )
            )
        ) {
            throw IntegrationException.forbidden(
                    "A Team Leader may use only a GitHub App installation they completed"
            );
        }
    }

    private com.saga.be.entity.Student projectStudent(SagaPrincipal principal) {
        return studentRepository.findById(principal.localProfileId())
                .orElseThrow(() -> IntegrationException.invalid(
                        "STUDENT_NOT_FOUND",
                        "The authenticated Student profile does not exist"
                ));
    }

    private URI jiraWebhookCallback(String secret) {
        String base = urlResolver.jiraWebhookPublicUrl();
        if (base == null || base.isBlank()) {
            throw new IntegrationException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "JIRA_WEBHOOK_URL_NOT_CONFIGURED",
                    "Jira webhook public URL is not configured"
            );
        }
        return UriComponentsBuilder.fromUriString(base)
                .queryParam("token", secret)
                .build()
                .encode()
                .toUri();
    }

    private void requireConfiguredJiraScopes() {
        Set<String> required = new HashSet<>(JiraWriteScope.projectIntegrationScopes());
        required.add(JiraWriteScope.OFFLINE_ACCESS_SCOPE);
        JiraWriteScope.requireGranted(
                JiraWriteScope.scopes(jiraProperties.scopes()),
                required.toArray(String[]::new)
        );
    }

    private void requireJiraLinkScopes(Set<String> resourceScopes) {
        JiraWriteScope.requireGranted(
                resourceScopes,
                JiraWriteScope.linkScopes().toArray(String[]::new)
        );
    }

    private void logJiraLinkFailure(
            UUID projectId,
            JiraLinkStage stage,
            String verifiedCloudId,
            Set<String> missingScopeNames,
            IntegrationException exception
    ) {
        log.warn("Jira link failed: projectId={}, stage={}, providerOperation={}, cloudId={}, "
                        + "upstreamHttpStatus={}, providerErrorCategory={}, requiredScopeCount={}, missingScopeNames={}",
                projectId, stage, stage.providerOperation, verifiedCloudId,
                exception.getStatus().value(), exception.getCode(),
                JiraWriteScope.linkScopes().size(), missingScopeNames);
    }

    private enum JiraLinkStage {
        LOAD_FRESH_GRANT("sessionGrant"),
        VERIFY_ACCESSIBLE_RESOURCE("accessibleResources"),
        SCOPE_PREFLIGHT("scopePreflight"),
        RESOLVE_PROJECT("resolveProject"),
        DISCOVER_SCRUM_BOARDS("discoverScrumBoards"),
        UPSERT_JIRA_BOARD("upsertJiraBoard"),
        REGISTER_WEBHOOK("registerWebhook");

        private final String providerOperation;

        JiraLinkStage(String providerOperation) {
            this.providerOperation = providerOperation;
        }
    }

    private JiraBoard upsertJiraBoard(JiraBoardLinkCommand command, UUID projectId) {
        try {
            return jiraBoardLinkPersistenceService.upsert(command);
        } catch (DataIntegrityViolationException firstRace) {
            log.warn("Jira board link race reconciled: projectId={}, stage=UPSERT_JIRA_BOARD, "
                    + "conflictType=SAME_PROJECT_RELINK_OR_PROVIDER_CONFLICT", projectId);
            try {
                return jiraBoardLinkPersistenceService.upsert(command);
            } catch (DataIntegrityViolationException secondRace) {
                throw IntegrationException.conflict(
                        "JIRA_BOARD_UPSERT_CONFLICT",
                        "The Jira project link could not be reconciled"
                );
            }
        }
    }

    private void compensateCreatedWebhook(
            JiraWebhookRegistration registration,
            String accessToken,
            String cloudId,
            UUID projectId
    ) {
        if (registration == null || !registration.created()) {
            return;
        }
        try {
            jiraClient.deleteWebhook(accessToken, cloudId, registration.webhookId());
        } catch (RuntimeException cleanupFailure) {
            // Never expose OAuth material. The periodic webhook maintenance
            // flow can reconcile a later link attempt; retain only identifiers.
            log.error(
                    "Could not compensate Jira webhook after database failure: "
                            + "projectId={}, cloudId={}, webhookId={}",
                    projectId,
                    cloudId,
                    registration.webhookId()
            );
        }
    }

    private String jiraCallbackUrl() {
        return jiraProperties.callbackUrl();
    }

    private void requireConsent(String code, String oauthError) {
        if (oauthError != null || code == null || code.isBlank()) {
            throw IntegrationException.invalid(
                    "OAUTH_CONSENT_DENIED",
                    "Provider authorization was denied or cancelled"
            );
        }
    }

    private void limit(SagaPrincipal principal, String operation) {
        attemptLimiter.requireAllowed(principal.cognitoSub() + ":" + operation);
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void dispatchAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            action.run();
        }
    }
}
