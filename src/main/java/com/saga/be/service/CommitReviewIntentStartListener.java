package com.saga.be.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommitReviewIntentStartListener {

    private final CommitReviewOrchestrator orchestrator;

    public CommitReviewIntentStartListener(CommitReviewOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(CommitReviewIntentQueued event) {
        if (event != null) {
            orchestrator.startQueued(event.intentId());
        }
    }
}
