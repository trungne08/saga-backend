package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;
import java.util.UUID;

public interface AutomaticSyncDispatcher {
    void initialJiraBackfill(UUID boardId);

    void initialGitHubBackfill(UUID repositoryLocalId);

    void reconcileJira(UUID boardId);

    void reconcileGitHub(UUID repositoryLocalId);

    void syncClaimedJira(UUID boardId, SyncJobLog job);

    void syncClaimedGitHub(UUID repositoryLocalId, SyncJobLog job);
}
