package com.saga.be.service;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitRepo;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import com.saga.be.repository.GitRepoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitReviewHistoricalDiscoveryService {

    private final CommitDataRepository commits;
    private final CommitReviewIntentService intents;
    private final CommitReviewIntentRepository intentRepository;
    private final CommitReviewResultRepository results;
    private final GitRepoRepository repos;
    private final CommitReviewWarningPublisher warnings;

    public CommitReviewHistoricalDiscoveryService(
            CommitDataRepository commits,
            CommitReviewIntentService intents,
            CommitReviewIntentRepository intentRepository,
            CommitReviewResultRepository results,
            GitRepoRepository repos,
            CommitReviewWarningPublisher warnings
    ) {
        this.commits = commits;
        this.intents = intents;
        this.intentRepository = intentRepository;
        this.results = results;
        this.repos = repos;
        this.warnings = warnings;
    }

    @Transactional
    public int discoverBoundedPage() {
        List<CommitData> page = commits.findHistoricalBacklogWithoutIntent(
                PageRequest.of(0, EarlyWarningPolicy.HISTORICAL_DISCOVERY_PAGE)
        );
        int created = 0;
        for (CommitData commit : page) {
            if (commit.getRepo() == null) {
                continue;
            }
            if (intentRepository.findByRepoIdAndShaHash(commit.getRepo().getId(), commit.getShaHash()).isPresent()) {
                continue;
            }
            if (intents.enqueueNewCanonicalCommit(commit.getRepo(), commit).isPresent()) {
                created++;
            }
        }
        return created;
    }

    @Transactional
    public void publishBoundedDigests() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        List<UUID> repoIds = results.findHistoricalRepoIdsInWindow(from, to);
        int published = 0;
        for (UUID repoId : repoIds) {
            if (published >= EarlyWarningPolicy.HISTORICAL_DIGEST_REPO_LIMIT) {
                break;
            }
            GitRepo repo = repos.findById(repoId).orElse(null);
            if (repo == null) {
                continue;
            }
            List<CommitReviewResult> window = results.findHistoricalCompletedInWindow(repoId, from, to);
            if (window.isEmpty()) {
                continue;
            }
            long codeRisk = window.stream().filter(row -> "RISKS".equals(row.getCodeQuality())).count();
            long poorMessage = window.stream().filter(row -> "POOR".equals(row.getMessageQuality())).count();
            long insufficient = window.stream()
                    .filter(row -> "INSUFFICIENT_CONTEXT".equals(row.getCodeQuality())
                            || "INSUFFICIENT_CONTEXT".equals(row.getOverallStatus()))
                    .count();
            long highSeverity = window.stream().filter(this::hasHighSeverityFinding).count();
            warnings.publishHistoricalDigest(repo, new CommitReviewWarningPublisher.HistoricalDigest(
                    day.toString(),
                    window.size(),
                    codeRisk,
                    poorMessage,
                    insufficient,
                    highSeverity
            ));
            published++;
        }
    }

    private boolean hasHighSeverityFinding(CommitReviewResult result) {
        String json = result.getFindingsJson();
        return json != null && json.contains("\"severity\":\"ERROR\"");
    }
}
