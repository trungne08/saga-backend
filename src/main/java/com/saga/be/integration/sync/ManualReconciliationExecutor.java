package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ManualReconciliationExecutor {

    private final AutomaticSyncDispatcher dispatcher;

    public ManualReconciliationExecutor(AutomaticSyncDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Async
    public void reconcileJira(UUID boardId, SyncJobLog job) {
        dispatcher.syncClaimedJira(boardId, job);
    }

    @Async
    public void reconcileGitHub(UUID repositoryId, SyncJobLog job) {
        dispatcher.syncClaimedGitHub(repositoryId, job);
    }
}
