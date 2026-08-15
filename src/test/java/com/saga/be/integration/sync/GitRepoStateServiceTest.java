package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.repository.GitRepoRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitRepoStateServiceTest {

    @Test
    void degradeReloadsTheCurrentLockedEntityInsteadOfSavingCallerState() {
        GitRepoRepository repository = mock(GitRepoRepository.class);
        UUID repositoryId = UUID.randomUUID();
        GitRepo current = GitRepo.builder()
                .connectionStatus(IntegrationStatus.ACTIVE)
                .consecutiveFailures(4)
                .build();
        current.setId(repositoryId);
        when(repository.findForStateUpdateById(repositoryId))
                .thenReturn(Optional.of(current));
        GitRepoStateService service = new GitRepoStateService(repository);

        assertTrue(service.degrade(repositoryId));

        assertTrue(current.getConnectionStatus() == IntegrationStatus.DEGRADED);
        assertTrue(current.getConsecutiveFailures() == 5);
        verify(repository).saveAndFlush(current);
    }
}
