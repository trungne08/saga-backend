package com.saga.be.integration.sync;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IntegrationReconciliationSchedulerTest {

    @Test
    void doesNotQueryOrCallProvidersWhenReconciliationIsDisabled() {
        JiraBoardRepository boardRepository = mock(JiraBoardRepository.class);
        GitRepoRepository repositoryRepository = mock(GitRepoRepository.class);
        GitHubInstallationRepository installationRepository = mock(
                GitHubInstallationRepository.class
        );
        JiraWebhookMaintenanceService webhookMaintenance = mock(
                JiraWebhookMaintenanceService.class
        );
        AutomaticSyncDispatcher dispatcher = mock(AutomaticSyncDispatcher.class);
        GitHubProviderClient gitHubClient = mock(GitHubProviderClient.class);
        IntegrationAvailability availability = mock(
                IntegrationAvailability.class
        );
        IntegrationProperties properties = new IntegrationProperties(
                null,
                null,
                null,
                Duration.ofMinutes(10),
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                false,
                Duration.ofMinutes(5)
        );
        IntegrationReconciliationScheduler scheduler =
                new IntegrationReconciliationScheduler(
                        properties,
                        availability,
                        boardRepository,
                        repositoryRepository,
                        installationRepository,
                        gitHubClient,
                        webhookMaintenance,
                        dispatcher,
                        mock(GitRepoStateService.class)
                );

        scheduler.reconcile();

        verifyNoInteractions(
                boardRepository,
                repositoryRepository,
                installationRepository,
                gitHubClient,
                webhookMaintenance,
                dispatcher
        );
    }
}
