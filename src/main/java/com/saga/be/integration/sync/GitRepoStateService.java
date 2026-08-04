package com.saga.be.integration.sync;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.repository.GitRepoRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitRepoStateService {

    private final GitRepoRepository gitRepoRepository;

    public GitRepoStateService(GitRepoRepository gitRepoRepository) {
        this.gitRepoRepository = gitRepoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(UUID repositoryId, LocalDateTime cursor) {
        return gitRepoRepository.findForStateUpdateById(repositoryId)
                .map(repository -> {
                    if (repository.getConnectionStatus()
                            == IntegrationStatus.DISCONNECTED) {
                        return false;
                    }
                    repository.setSyncCursor(cursor);
                    repository.setLastSyncedAt(LocalDateTime.now());
                    repository.setConnectionStatus(IntegrationStatus.ACTIVE);
                    repository.setConsecutiveFailures(0);
                    gitRepoRepository.saveAndFlush(repository);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean degrade(UUID repositoryId) {
        return gitRepoRepository.findForStateUpdateById(repositoryId)
                .map(repository -> {
                    if (repository.getConnectionStatus()
                            == IntegrationStatus.DISCONNECTED) {
                        return false;
                    }
                    repository.setConsecutiveFailures(
                            repository.getConsecutiveFailures() + 1
                    );
                    repository.setConnectionStatus(IntegrationStatus.DEGRADED);
                    gitRepoRepository.saveAndFlush(repository);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDegraded(UUID repositoryId) {
        return gitRepoRepository.findForStateUpdateById(repositoryId)
                .map(repository -> {
                    if (repository.getConnectionStatus()
                            == IntegrationStatus.DISCONNECTED) {
                        return false;
                    }
                    repository.setConnectionStatus(IntegrationStatus.DEGRADED);
                    gitRepoRepository.saveAndFlush(repository);
                    return true;
                })
                .orElse(false);
    }
}
