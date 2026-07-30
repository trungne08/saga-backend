package com.saga.be.integration.project;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GitHubInitialBackfillListener {

    private final AutomaticSyncDispatcher syncDispatcher;
    private final IntegrationAvailability availability;

    public GitHubInitialBackfillListener(
            AutomaticSyncDispatcher syncDispatcher,
            IntegrationAvailability availability
    ) {
        this.syncDispatcher = syncDispatcher;
        this.availability = availability;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInitialBackfillRequested(
            GitHubInitialBackfillRequested event
    ) {
        if (availability.gitHubEnabled()) {
            syncDispatcher.initialGitHubBackfill(event.repositoryLocalId());
        }
    }
}
