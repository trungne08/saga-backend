package com.saga.be.service;

import com.saga.be.dto.response.CommitReviewSummary;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch-resolves public {@link CommitReviewSummary} projections for a page of provider commit
 * SHAs in a single repo, using at most three repository queries regardless of page size (no
 * per-commit lookups).
 */
@Service
public class CommitReviewSummaryResolver {

    private final CommitDataRepository commitDataRepository;
    private final CommitReviewIntentRepository intentRepository;
    private final CommitReviewResultRepository resultRepository;

    public CommitReviewSummaryResolver(
            CommitDataRepository commitDataRepository,
            CommitReviewIntentRepository intentRepository,
            CommitReviewResultRepository resultRepository
    ) {
        this.commitDataRepository = commitDataRepository;
        this.intentRepository = intentRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, CommitReviewSummary> resolve(UUID repoId, Collection<String> shaHashes) {
        if (repoId == null || shaHashes == null || shaHashes.isEmpty()) {
            return Map.of();
        }
        Set<String> requestedShas = new LinkedHashSet<>();
        for (String sha : shaHashes) {
            if (sha != null && !sha.isBlank()) {
                requestedShas.add(sha);
            }
        }
        if (requestedShas.isEmpty()) {
            return Map.of();
        }

        List<CommitData> localCommits = commitDataRepository.findByRepoIdAndShaHashIn(repoId, requestedShas);
        if (localCommits.isEmpty()) {
            return Map.of();
        }
        Set<String> localShas = localCommits.stream().map(CommitData::getShaHash).collect(Collectors.toSet());

        List<CommitReviewIntent> intents = intentRepository.findByRepoIdAndShaHashIn(repoId, localShas);
        if (intents.isEmpty()) {
            return Map.of();
        }

        List<UUID> intentIds = intents.stream().map(CommitReviewIntent::getId).toList();
        Map<UUID, CommitReviewResult> resultsByIntentId = resultRepository.findByIntentIdIn(intentIds).stream()
                .collect(Collectors.toMap(result -> result.getIntent().getId(), Function.identity(), (a, b) -> a));

        Map<String, CommitReviewSummary> summariesByShaHash = new HashMap<>();
        for (CommitReviewIntent intent : intents) {
            CommitReviewResult result = resultsByIntentId.get(intent.getId());
            summariesByShaHash.put(intent.getShaHash(), toSummary(intent, result));
        }
        return Map.copyOf(summariesByShaHash);
    }

    private CommitReviewSummary toSummary(CommitReviewIntent intent, CommitReviewResult result) {
        CommitReviewIntentStatus status = intent.getIntentStatus();
        // FAILED/CANCELLED are processing outcomes, never a NEEDS_CHANGES-style verdict; force
        // result=null even if a result row unexpectedly exists, so it can never surface here.
        boolean suppressResult = status == CommitReviewIntentStatus.FAILED
                || status == CommitReviewIntentStatus.CANCELLED;
        CommitReviewResult effectiveResult = suppressResult ? null : result;

        CommitReviewSummary.Result resultSummary = effectiveResult == null
                ? null
                : new CommitReviewSummary.Result(
                        effectiveResult.getTraceabilityStatus(),
                        effectiveResult.getMessageQuality(),
                        effectiveResult.getCodeQuality(),
                        effectiveResult.getTaskAlignment(),
                        effectiveResult.isVerdictEligible(),
                        effectiveResult.getVerdict(),
                        effectiveResult.getOverallStatus()
                );
        String reviewMode = effectiveResult == null ? null : effectiveResult.getReviewMode();

        return new CommitReviewSummary(
                status,
                reviewMode,
                toInstant(intent.getStartedAt()),
                toInstant(intent.getCompletedAt()),
                resultSummary
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
