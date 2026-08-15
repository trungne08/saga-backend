package com.saga.be.service;

import com.saga.be.dto.response.CommitReviewJobResponses;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.CommitReviewIntentRepository;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CommitReviewOrchestrator.class);

    private final CommitReviewIntentService intents;
    private final CommitReviewIntentRepository intentRepository;
    private final CommitReviewAiClient client;

    public CommitReviewOrchestrator(
            CommitReviewIntentService intents,
            CommitReviewIntentRepository intentRepository,
            CommitReviewAiClient client
    ) {
        this.intents = intents;
        this.intentRepository = intentRepository;
        this.client = client;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void startQueued(UUID intentId) {
        if (intentId == null || !client.isConfigured() || intents.claimPendingForStart(intentId).isEmpty()) {
            return;
        }
        startClaimed(intentId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void drainPendingAndPoll() {
        if (!client.isConfigured()) {
            return;
        }
        for (CommitReviewIntent pending : intents.nextWorkAvoidingHistoricalStarvation()) {
            startQueued(pending.getId());
        }
        try {
            client.runBounded();
        } catch (RuntimeException exception) {
            logSafe("bounded execution trigger failed", exception);
        }
        for (CommitReviewIntent inFlight : intents.nextInFlight(
                CommitReviewIntentService.LIVE_DRAIN_LIMIT + CommitReviewIntentService.HISTORICAL_DRAIN_LIMIT
        )) {
            poll(inFlight.getId());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void poll(UUID intentId) {
        CommitReviewIntent intent = intentRepository.findById(intentId).orElse(null);
        if (intent == null || intent.getAiJobId() == null) {
            return;
        }
        try {
            CommitReviewJobResponses.Status status = client.status(intent.getAiJobId());
            intents.markPolled(intentId, status);
        } catch (CommitReviewResultParser.CommitReviewResultRejected rejected) {
            intents.markFailed(intentId, rejected.code());
        } catch (CommitReviewPolicyVersion.CommitReviewContractRejected rejected) {
            intents.markFailed(intentId, rejected.code());
        } catch (IntegrationException exception) {
            logSafe("commit-review status poll failed", exception);
        } catch (RuntimeException exception) {
            logSafe("commit-review status poll failed", exception);
        }
    }

    private void startClaimed(UUID intentId) {
        CommitReviewIntent intent = intentRepository.findWithReviewTargetById(intentId).orElse(null);
        if (intent == null) {
            return;
        }
        try {
            GitRepo repository = intent.getRepo();
            Project project = repository == null ? null : repository.getProject();
            if (project == null || project.getId() == null
                    || repository.getRepositoryId() == null
                    || intent.getShaHash() == null) {
                intents.markFailed(intentId, "AI_REVIEW_TARGET_INVALID");
                return;
            }
            CommitReviewPolicyVersion policy = CommitReviewPolicyVersion.forMode(intent.getReviewMode());
            CommitReviewJobResponses.Start started = client.start(
                    project.getId(),
                    repository.getRepositoryId(),
                    intent.getShaHash().toLowerCase(Locale.ROOT),
                    policy
            );
            intents.markStarted(intentId, started.jobId(), policy.wireValue(), started.status());
            client.runBounded();
        } catch (CommitReviewPolicyVersion.CommitReviewContractRejected rejected) {
            intents.markFailed(intentId, rejected.code());
        } catch (CommitReviewResultParser.CommitReviewResultRejected rejected) {
            intents.markFailed(intentId, rejected.code());
        } catch (IntegrationException exception) {
            intents.restorePending(intentId, exception.getCode());
        } catch (RuntimeException exception) {
            intents.restorePending(intentId, "AI_AGENT_UNAVAILABLE");
            logSafe("commit-review start failed", exception);
        }
    }

    private void logSafe(String message, RuntimeException exception) {
        String code = exception instanceof IntegrationException integration
                ? integration.getCode()
                : exception.getClass().getSimpleName();
        log.warn("commit-review orchestration: {} code={}", message, code);
    }
}
