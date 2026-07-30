package com.saga.be.integration.sync;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.integration.provider.GitHubInstallationInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubRepositoryInfo;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntegrationReconciliationScheduler {

    private final IntegrationProperties properties;
    private final IntegrationAvailability availability;
    private final JiraBoardRepository jiraBoardRepository;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GitHubProviderClient gitHubClient;
    private final JiraWebhookMaintenanceService webhookMaintenanceService;
    private final AutomaticSyncDispatcher dispatcher;

    public IntegrationReconciliationScheduler(
            IntegrationProperties properties,
            IntegrationAvailability availability,
            JiraBoardRepository jiraBoardRepository,
            GitRepoRepository gitRepoRepository,
            GitHubInstallationRepository installationRepository,
            GitHubProviderClient gitHubClient,
            JiraWebhookMaintenanceService webhookMaintenanceService,
            AutomaticSyncDispatcher dispatcher
    ) {
        this.properties = properties;
        this.availability = availability;
        this.jiraBoardRepository = jiraBoardRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.installationRepository = installationRepository;
        this.gitHubClient = gitHubClient;
        this.webhookMaintenanceService = webhookMaintenanceService;
        this.dispatcher = dispatcher;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.integrations.reconciliation-delay-ms:900000}"
    )
    public void reconcile() {
        if (!properties.reconciliationEnabled()) {
            return;
        }
        List<IntegrationStatus> managed = List.of(
                IntegrationStatus.ACTIVE,
                IntegrationStatus.DEGRADED,
                IntegrationStatus.BACKFILLING
        );
        if (availability.jiraEnabled()) {
            for (JiraBoard board : jiraBoardRepository
                    .findByConnectionStatusIn(managed)) {
                if (
                    board.getWebhookExpiresAt() == null
                    || board.getWebhookExpiresAt().isBefore(
                            LocalDateTime.now().plusDays(3)
                    )
                ) {
                    webhookMaintenanceService.refresh(board.getId());
                }
                dispatcher.reconcileJira(board.getId());
            }
        }

        if (availability.gitHubEnabled()) {
            for (GitHubInstallation installation : installationRepository
                    .findByInstallationStatus(GitHubInstallationStatus.ACTIVE)) {
                verifyInstallation(installation);
            }
        }
    }

    private void verifyInstallation(GitHubInstallation installation) {
        try {
            GitHubInstallationInfo current = gitHubClient.installation(
                    installation.getInstallationId()
            );
            if (current.suspended()) {
                installation.setInstallationStatus(
                        GitHubInstallationStatus.SUSPENDED
                );
                installationRepository.saveAndFlush(installation);
                markRepositoriesDegraded(installation);
                return;
            }
            Set<Long> accessibleIds = gitHubClient.installationRepositories(
                            installation.getInstallationId()
                    )
                    .stream()
                    .map(GitHubRepositoryInfo::id)
                    .collect(Collectors.toSet());
            installation.setLastVerifiedAt(LocalDateTime.now());
            installation.setConsecutiveFailures(0);
            installationRepository.saveAndFlush(installation);
            for (GitRepo repository : gitRepoRepository
                    .findByInstallationInstallationId(
                            installation.getInstallationId()
                    )) {
                if (!accessibleIds.contains(repository.getRepositoryId())) {
                    repository.setConnectionStatus(IntegrationStatus.DEGRADED);
                    gitRepoRepository.saveAndFlush(repository);
                } else if (repository.getConnectionStatus()
                        != IntegrationStatus.DISCONNECTED) {
                    dispatcher.reconcileGitHub(repository.getId());
                }
            }
        } catch (RuntimeException exception) {
            installation.setConsecutiveFailures(
                    installation.getConsecutiveFailures() + 1
            );
            installationRepository.saveAndFlush(installation);
            if (installation.getConsecutiveFailures() >= 3) {
                markRepositoriesDegraded(installation);
            }
        }
    }

    private void markRepositoriesDegraded(GitHubInstallation installation) {
        for (GitRepo repository : gitRepoRepository
                .findByInstallationInstallationId(
                        installation.getInstallationId()
                )) {
            if (repository.getConnectionStatus()
                    != IntegrationStatus.DISCONNECTED) {
                repository.setConnectionStatus(IntegrationStatus.DEGRADED);
                gitRepoRepository.saveAndFlush(repository);
            }
        }
    }
}
