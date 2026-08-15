package com.saga.be.service;

import com.saga.be.dto.response.CommitReviewJobResponses;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.repository.CommitReviewIntentRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitReviewIntentService {

    static final int LIVE_DRAIN_LIMIT = 20;
    static final int HISTORICAL_DRAIN_LIMIT = 5;

    private final CommitReviewIntentRepository intents;
    private final ApplicationEventPublisher events;

    public CommitReviewIntentService(
            CommitReviewIntentRepository intents,
            ApplicationEventPublisher events
    ) {
        this.intents = intents;
        this.events = events;
    }

    @Transactional
    public Optional<CommitReviewIntent> enqueueNewCanonicalCommit(GitRepo repository, CommitData commit) {
        if (repository == null || repository.getId() == null || commit == null || commit.getId() == null) {
            return Optional.empty();
        }
        String sha = commit.getShaHash();
        if (sha == null || sha.isBlank()) {
            return Optional.empty();
        }
        Optional<CommitReviewIntent> existing = intents.findByRepoIdAndShaHash(repository.getId(), sha);
        if (existing.isPresent()) {
            return existing;
        }
        return CommitReviewClassifier.classify(commit.getTimestamp(), repository.getReviewCutoverAt())
                .map(classification -> {
                    CommitReviewIntent created = intents.saveAndFlush(CommitReviewIntent.builder()
                            .repo(repository)
                            .commit(commit)
                            .shaHash(sha)
                            .reviewMode(classification.mode())
                            .priority(classification.priority())
                            .priorityRank(classification.priorityRank())
                            .intentStatus(CommitReviewIntentStatus.PENDING)
                            .build());
                    events.publishEvent(new CommitReviewIntentQueued(created.getId()));
                    return created;
                });
    }

    @Transactional
    public Optional<UUID> claimPendingForStart(UUID intentId) {
        if (intentId == null) {
            return Optional.empty();
        }
        CommitReviewIntent intent = intents.findLockedById(intentId).orElse(null);
        if (intent == null || intent.getIntentStatus() != CommitReviewIntentStatus.PENDING) {
            return Optional.empty();
        }
        intent.setIntentStatus(CommitReviewIntentStatus.STARTING);
        intents.saveAndFlush(intent);
        return Optional.of(intentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(UUID intentId, UUID jobId, String policyVersion, String jobStatus) {
        CommitReviewIntent intent = intents.findLockedById(intentId).orElseThrow();
        intent.setAiJobId(jobId);
        intent.setReviewPolicyVersion(policyVersion);
        intent.setLastJobStatus(jobStatus);
        intent.setIntentStatus(mapInFlight(jobStatus));
        intent.setStartedAt(LocalDateTime.now());
        intent.setSafeErrorCode(null);
        intents.saveAndFlush(intent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restorePending(UUID intentId, String safeErrorCode) {
        CommitReviewIntent intent = intents.findLockedById(intentId).orElse(null);
        if (intent == null || intent.getIntentStatus() != CommitReviewIntentStatus.STARTING) {
            return;
        }
        intent.setIntentStatus(CommitReviewIntentStatus.PENDING);
        intent.setSafeErrorCode(safeErrorCode);
        intents.saveAndFlush(intent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPolled(UUID intentId, CommitReviewJobResponses.Status status) {
        CommitReviewIntent intent = intents.findLockedById(intentId).orElseThrow();
        String jobStatus = status.status();
        intent.setLastJobStatus(jobStatus);
        intent.setSafeErrorCode(status.safeErrorCode());
        if ("COMPLETED".equals(jobStatus)) {
            intent.setIntentStatus(CommitReviewIntentStatus.COMPLETED);
            intent.setCompletedAt(LocalDateTime.now());
        } else if ("FAILED".equals(jobStatus)) {
            intent.setIntentStatus(CommitReviewIntentStatus.FAILED);
            intent.setCompletedAt(LocalDateTime.now());
        } else if ("CANCELLED".equals(jobStatus)) {
            intent.setIntentStatus(CommitReviewIntentStatus.CANCELLED);
            intent.setCompletedAt(LocalDateTime.now());
        } else {
            intent.setIntentStatus(mapInFlight(jobStatus));
        }
        intents.saveAndFlush(intent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID intentId, String safeErrorCode) {
        CommitReviewIntent intent = intents.findLockedById(intentId).orElse(null);
        if (intent == null) {
            return;
        }
        intent.setIntentStatus(CommitReviewIntentStatus.FAILED);
        intent.setSafeErrorCode(safeErrorCode);
        intent.setCompletedAt(LocalDateTime.now());
        intents.saveAndFlush(intent);
    }

    @Transactional(readOnly = true)
    public List<CommitReviewIntent> nextWorkAvoidingHistoricalStarvation() {
        List<CommitReviewIntent> ready = intents.findReady(
                CommitReviewIntentStatus.PENDING,
                PageRequest.of(0, LIVE_DRAIN_LIMIT + HISTORICAL_DRAIN_LIMIT)
        );
        List<CommitReviewIntent> live = new ArrayList<>();
        List<CommitReviewIntent> historical = new ArrayList<>();
        for (CommitReviewIntent intent : ready) {
            if (intent.getPriorityRank() >= CommitReviewClassifier.LIVE_PRIORITY_RANK) {
                if (live.size() < LIVE_DRAIN_LIMIT) {
                    live.add(intent);
                }
            } else if (historical.size() < HISTORICAL_DRAIN_LIMIT) {
                historical.add(intent);
            }
        }
        List<CommitReviewIntent> selected = new ArrayList<>(live);
        selected.addAll(historical);
        return List.copyOf(selected);
    }

    @Transactional(readOnly = true)
    public List<CommitReviewIntent> nextInFlight(int limit) {
        return List.copyOf(intents.findByIntentStatusIn(
                List.of(
                        CommitReviewIntentStatus.STARTED,
                        CommitReviewIntentStatus.RUNNING,
                        CommitReviewIntentStatus.WAITING_RETRY
                ),
                PageRequest.of(0, Math.max(1, limit))
        ));
    }

    private CommitReviewIntentStatus mapInFlight(String jobStatus) {
        return switch (jobStatus) {
            case "RUNNING" -> CommitReviewIntentStatus.RUNNING;
            case "WAITING_RETRY" -> CommitReviewIntentStatus.WAITING_RETRY;
            case "COMPLETED" -> CommitReviewIntentStatus.COMPLETED;
            case "FAILED" -> CommitReviewIntentStatus.FAILED;
            case "CANCELLED" -> CommitReviewIntentStatus.CANCELLED;
            default -> CommitReviewIntentStatus.STARTED;
        };
    }
}
