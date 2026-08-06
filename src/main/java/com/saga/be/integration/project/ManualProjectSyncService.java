package com.saga.be.integration.project;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.dto.request.ManualSyncProvider;
import com.saga.be.dto.response.ManualProjectSyncResponse;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.GitHubSyncJobService;
import com.saga.be.integration.sync.JiraSyncJobService;
import com.saga.be.integration.sync.ManualReconciliationExecutor;
import com.saga.be.integration.sync.SyncJobClaimResult;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManualProjectSyncService {

    private final ProjectIntegrationAuthorizationService authorization;
    private final IntegrationAvailability availability;
    private final JiraBoardRepository jiraBoardRepository;
    private final GitRepoRepository gitRepoRepository;
    private final JiraSyncJobService jiraSyncJobService;
    private final GitHubSyncJobService gitHubSyncJobService;
    private final ManualReconciliationExecutor executor;

    public ManualProjectSyncService(
            ProjectIntegrationAuthorizationService authorization,
            IntegrationAvailability availability,
            JiraBoardRepository jiraBoardRepository,
            GitRepoRepository gitRepoRepository,
            JiraSyncJobService jiraSyncJobService,
            GitHubSyncJobService gitHubSyncJobService,
            ManualReconciliationExecutor executor
    ) {
        this.authorization = authorization;
        this.availability = availability;
        this.jiraBoardRepository = jiraBoardRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.jiraSyncJobService = jiraSyncJobService;
        this.gitHubSyncJobService = gitHubSyncJobService;
        this.executor = executor;
    }

    public ManualProjectSyncResponse request(
            SagaPrincipal principal,
            UUID projectId,
            ManualSyncProvider provider
    ) {
        authorization.requireProjectManager(principal, projectId);
        List<SyncTarget> targets = targets(projectId, provider);
        List<ManualProjectSyncResponse.Target> accepted = new ArrayList<>();
        for (SyncTarget target : targets) {
            accepted.add(target.claimAndDispatch());
        }
        return new ManualProjectSyncResponse(projectId, provider, true, List.copyOf(accepted));
    }

    private List<SyncTarget> targets(UUID projectId, ManualSyncProvider provider) {
        JiraBoard board = jiraBoardRepository.findByProjectId(projectId).orElse(null);
        List<GitRepo> repositories = gitRepoRepository
                .findByProjectIdOrderByFullName(projectId)
                .stream()
                .filter(repository -> repository.getConnectionStatus()
                        != IntegrationStatus.DISCONNECTED)
                .toList();
        boolean jiraLinked = board != null && board.getConnectionStatus()
                != IntegrationStatus.DISCONNECTED;

        return switch (provider) {
            case JIRA -> List.of(jiraTarget(requireJira(board, jiraLinked)));
            case GITHUB -> gitHubTargets(requireGitHub(repositories));
            case ALL -> allTargets(board, jiraLinked, repositories);
        };
    }

    private List<SyncTarget> allTargets(
            JiraBoard board,
            boolean jiraLinked,
            List<GitRepo> repositories
    ) {
        if (!jiraLinked && repositories.isEmpty()) {
            throw IntegrationException.conflict(
                    "PROJECT_SYNC_INTEGRATION_MISSING",
                    "The project has no active integration to synchronize"
            );
        }
        List<SyncTarget> targets = new ArrayList<>();
        if (jiraLinked) {
            targets.add(jiraTarget(requireJira(board, true)));
        }
        if (!repositories.isEmpty()) {
            targets.addAll(gitHubTargets(requireGitHub(repositories)));
        }
        return List.copyOf(targets);
    }

    private JiraBoard requireJira(JiraBoard board, boolean linked) {
        availability.requireJira();
        if (!linked) {
            throw IntegrationException.conflict(
                    "JIRA_INTEGRATION_NOT_LINKED",
                    "The project has no active Jira integration"
            );
        }
        return board;
    }

    private List<GitRepo> requireGitHub(List<GitRepo> repositories) {
        availability.requireGitHub();
        if (repositories.isEmpty()) {
            throw IntegrationException.conflict(
                    "GITHUB_INTEGRATION_NOT_LINKED",
                    "The project has no active GitHub repository integration"
            );
        }
        return repositories;
    }

    private SyncTarget jiraTarget(JiraBoard board) {
        return new SyncTarget() {
            @Override
            public ManualProjectSyncResponse.Target claimAndDispatch() {
                SyncJobClaimResult result = jiraSyncJobService
                        .claimOrReuse(board.getId(), SyncJobType.RECONCILIATION)
                        .orElseThrow(() -> unavailable("JIRA"));
                if (!result.coalesced()) {
                    executor.reconcileJira(board.getId(), result.job());
                }
                return target(result);
            }
        };
    }

    private List<SyncTarget> gitHubTargets(List<GitRepo> repositories) {
        return repositories.stream().<SyncTarget>map(repository -> () -> {
            SyncJobClaimResult result = gitHubSyncJobService
                    .claimOrReuse(repository.getId(), SyncJobType.RECONCILIATION)
                    .orElseThrow(() -> unavailable("GITHUB"));
            if (!result.coalesced()) {
                executor.reconcileGitHub(repository.getId(), result.job());
            }
            return target(result);
        }).toList();
    }

    private ManualProjectSyncResponse.Target target(SyncJobClaimResult result) {
        return new ManualProjectSyncResponse.Target(
                result.job().getId(),
                result.job().getTargetSystem(),
                result.job().getStatus(),
                result.coalesced()
        );
    }

    private IntegrationException unavailable(String provider) {
        return IntegrationException.conflict(
                provider + "_SYNC_TARGET_UNAVAILABLE",
                "The requested integration is no longer available"
        );
    }

    @FunctionalInterface
    private interface SyncTarget {
        ManualProjectSyncResponse.Target claimAndDispatch();
    }
}
