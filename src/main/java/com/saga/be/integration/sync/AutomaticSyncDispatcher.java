package com.saga.be.integration.sync;

import java.util.UUID;

public interface AutomaticSyncDispatcher {
    void initialJiraBackfill(UUID boardId);

    void initialGitHubBackfill(UUID repositoryLocalId);

    void reconcileJira(UUID boardId);

    void reconcileGitHub(UUID repositoryLocalId);
}
