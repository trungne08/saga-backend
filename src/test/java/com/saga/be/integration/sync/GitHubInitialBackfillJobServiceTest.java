package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubInitialBackfillJobServiceTest {

    @Test
    void delegatesTheInitialBackfillClaimToTheSharedGitHubGuard() {
        GitHubSyncJobService syncJobService = mock(GitHubSyncJobService.class);
        GitHubInitialBackfillJobService service =
                new GitHubInitialBackfillJobService(syncJobService);
        UUID repositoryId = UUID.randomUUID();
        SyncJobLog job = SyncJobLog.builder()
                .status(SyncJobStatus.IN_PROGRESS)
                .build();
        when(syncJobService.claim(
                repositoryId,
                SyncJobType.INITIAL_BACKFILL
        )).thenReturn(Optional.of(job));

        Optional<SyncJobLog> claimed = service.claim(repositoryId);

        assertTrue(claimed.isPresent());
        assertEquals(job, claimed.orElseThrow());
        verify(syncJobService).claim(
                repositoryId,
                SyncJobType.INITIAL_BACKFILL
        );
    }
}
