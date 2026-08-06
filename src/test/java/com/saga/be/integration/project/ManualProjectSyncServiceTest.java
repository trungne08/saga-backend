package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.dto.request.ManualSyncProvider;
import com.saga.be.dto.response.ManualProjectSyncResponse;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualProjectSyncServiceTest {

    private ProjectIntegrationAuthorizationService authorization;
    private IntegrationAvailability availability;
    private JiraBoardRepository jiraBoardRepository;
    private GitRepoRepository gitRepoRepository;
    private JiraSyncJobService jiraSyncJobService;
    private GitHubSyncJobService gitHubSyncJobService;
    private ManualReconciliationExecutor executor;
    private ManualProjectSyncService service;
    private UUID projectId;
    private JiraBoard board;
    private GitRepo repository;

    @BeforeEach
    void setUp() {
        authorization = mock(ProjectIntegrationAuthorizationService.class);
        availability = mock(IntegrationAvailability.class);
        jiraBoardRepository = mock(JiraBoardRepository.class);
        gitRepoRepository = mock(GitRepoRepository.class);
        jiraSyncJobService = mock(JiraSyncJobService.class);
        gitHubSyncJobService = mock(GitHubSyncJobService.class);
        executor = mock(ManualReconciliationExecutor.class);
        service = new ManualProjectSyncService(
                authorization,
                availability,
                jiraBoardRepository,
                gitRepoRepository,
                jiraSyncJobService,
                gitHubSyncJobService,
                executor
        );
        projectId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(projectId);
        when(authorization.requireProjectManager(any(), eq(projectId)))
                .thenReturn(project);
        board = JiraBoard.builder().connectionStatus(IntegrationStatus.ACTIVE).build();
        board.setId(UUID.randomUUID());
        repository = GitRepo.builder().connectionStatus(IntegrationStatus.ACTIVE).build();
        repository.setId(UUID.randomUUID());
    }

    @Test
    void allClaimsEachIndependentTargetAndDispatchesThroughSharedExecutor() {
        when(jiraBoardRepository.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(gitRepoRepository.findByProjectIdOrderByFullName(projectId))
                .thenReturn(List.of(repository));
        SyncJobLog jiraJob = job("JIRA", board.getId());
        SyncJobLog gitHubJob = job("GITHUB", repository.getId());
        when(jiraSyncJobService.claimOrReuse(board.getId(), SyncJobType.RECONCILIATION))
                .thenReturn(Optional.of(SyncJobClaimResult.claimed(jiraJob)));
        when(gitHubSyncJobService.claimOrReuse(repository.getId(), SyncJobType.RECONCILIATION))
                .thenReturn(Optional.of(SyncJobClaimResult.claimed(gitHubJob)));

        ManualProjectSyncResponse response = service.request(
                mock(SagaPrincipal.class), projectId, ManualSyncProvider.ALL
        );

        assertEquals(2, response.targets().size());
        verify(executor).reconcileJira(board.getId(), jiraJob);
        verify(executor).reconcileGitHub(repository.getId(), gitHubJob);
    }

    @Test
    void activeClaimIsReportedAsCoalescedAndNotDispatchedAgain() {
        when(jiraBoardRepository.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(gitRepoRepository.findByProjectIdOrderByFullName(projectId)).thenReturn(List.of());
        SyncJobLog activeJob = job("JIRA", board.getId());
        when(jiraSyncJobService.claimOrReuse(board.getId(), SyncJobType.RECONCILIATION))
                .thenReturn(Optional.of(SyncJobClaimResult.coalesced(activeJob)));

        ManualProjectSyncResponse response = service.request(
                mock(SagaPrincipal.class), projectId, ManualSyncProvider.JIRA
        );

        assertEquals(activeJob.getId(), response.targets().get(0).jobId());
        assertEquals(true, response.targets().get(0).coalesced());
        verify(executor, never()).reconcileJira(any(), any());
        verifyNoInteractions(gitHubSyncJobService);
    }

    @Test
    void concurrentSecondRequestUsesCoalescedClaimInsteadOfCreatingOrDispatchingAnotherJob() {
        when(jiraBoardRepository.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(gitRepoRepository.findByProjectIdOrderByFullName(projectId)).thenReturn(List.of());
        SyncJobLog claimed = job("JIRA", board.getId());
        when(jiraSyncJobService.claimOrReuse(board.getId(), SyncJobType.RECONCILIATION))
                .thenReturn(Optional.of(SyncJobClaimResult.claimed(claimed)))
                .thenReturn(Optional.of(SyncJobClaimResult.coalesced(claimed)));

        service.request(mock(SagaPrincipal.class), projectId, ManualSyncProvider.JIRA);
        ManualProjectSyncResponse second = service.request(
                mock(SagaPrincipal.class), projectId, ManualSyncProvider.JIRA
        );

        assertEquals(true, second.targets().get(0).coalesced());
        verify(executor).reconcileJira(board.getId(), claimed);
    }

    @Test
    void authorizationFailureCreatesNoJobAndDoesNotDispatch() {
        when(authorization.requireProjectManager(any(), eq(projectId)))
                .thenThrow(IntegrationException.forbidden("Forbidden"));

        assertThrows(IntegrationException.class, () -> service.request(
                mock(SagaPrincipal.class), projectId, ManualSyncProvider.ALL
        ));

        verifyNoInteractions(jiraBoardRepository, gitRepoRepository,
                jiraSyncJobService, gitHubSyncJobService, executor);
    }

    @Test
    void requestedProviderWithoutIntegrationCreatesNoJob() {
        when(jiraBoardRepository.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(gitRepoRepository.findByProjectIdOrderByFullName(projectId)).thenReturn(List.of());

        assertThrows(IntegrationException.class, () -> service.request(
                mock(SagaPrincipal.class), projectId, ManualSyncProvider.GITHUB
        ));

        verifyNoInteractions(jiraSyncJobService, gitHubSyncJobService, executor);
    }

    private SyncJobLog job(String targetSystem, UUID targetId) {
        SyncJobLog job = SyncJobLog.builder()
                .targetSystem(targetSystem)
                .targetId(targetId)
                .jobType(SyncJobType.RECONCILIATION)
                .status(SyncJobStatus.IN_PROGRESS)
                .build();
        job.setId(UUID.randomUUID());
        return job;
    }
}
