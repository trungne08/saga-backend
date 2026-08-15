package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubInitialBackfillJobService {

    private final GitHubSyncJobService gitHubSyncJobService;

    @Autowired
    public GitHubInitialBackfillJobService(
            GitHubSyncJobService gitHubSyncJobService
    ) {
        this.gitHubSyncJobService = gitHubSyncJobService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobLog> claim(UUID repositoryLocalId) {
        return gitHubSyncJobService.claim(
                repositoryLocalId,
                SyncJobType.INITIAL_BACKFILL
        );
    }
}
