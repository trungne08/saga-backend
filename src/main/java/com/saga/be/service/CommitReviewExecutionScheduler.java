package com.saga.be.service;

import com.saga.be.config.CommitReviewAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.agent-ai.commit-review.execution-enabled", havingValue = "true", matchIfMissing = true)
public class CommitReviewExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommitReviewExecutionScheduler.class);

    private final CommitReviewOrchestrator orchestrator;
    private final CommitReviewAiProperties properties;

    public CommitReviewExecutionScheduler(
            CommitReviewOrchestrator orchestrator,
            CommitReviewAiProperties properties
    ) {
        this.orchestrator = orchestrator;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${app.agent-ai.commit-review.poll-delay:PT15S}",
            initialDelayString = "${app.agent-ai.commit-review.initial-delay:PT15S}"
    )
    public void drainAndPoll() {
        if (properties != null && !properties.executionEnabled()) {
            return;
        }
        try {
            orchestrator.drainPendingAndPoll();
        } catch (RuntimeException exception) {
            log.warn("commit-review scheduler tick failed type={}", exception.getClass().getSimpleName());
        }
    }
}
