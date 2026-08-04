package com.saga.be.integration.sync;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubInitialBackfillJobService {

    private final GitRepoRepository gitRepoRepository;
    private final SyncJobLogRepository jobRepository;
    private final Clock clock;

    @Autowired
    public GitHubInitialBackfillJobService(
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository
    ) {
        this(gitRepoRepository, jobRepository, Clock.systemUTC());
    }

    GitHubInitialBackfillJobService(
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository,
            Clock clock
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobLog> claim(UUID repositoryLocalId) {
        GitRepo repository = gitRepoRepository
                .findForInitialBackfillClaimById(repositoryLocalId)
                .orElse(null);
        if (
            repository == null
            || repository.getConnectionStatus() == IntegrationStatus.DISCONNECTED
            || (
                repository.getConnectionStatus() == IntegrationStatus.ACTIVE
                && repository.getLastSyncedAt() != null
            )
        ) {
            return Optional.empty();
        }

        SyncJobLog latest = jobRepository
                .findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
                        repositoryLocalId,
                        SyncJobType.INITIAL_BACKFILL
                )
                .orElse(null);
        if (
            latest != null
            && (
                latest.getStatus() == SyncJobStatus.PENDING
                || latest.getStatus() == SyncJobStatus.IN_PROGRESS
            )
        ) {
            return Optional.empty();
        }

        repository.setConnectionStatus(IntegrationStatus.BACKFILLING);
        gitRepoRepository.saveAndFlush(repository);
        return Optional.of(jobRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("GITHUB")
                .targetId(repositoryLocalId)
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(utcNow())
                .cursorBefore(repository.getSyncCursor())
                .build()));
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
