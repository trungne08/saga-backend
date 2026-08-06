package com.saga.be.integration.sync;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubSyncJobService {

    private static final List<SyncJobStatus> ACTIVE_STATUSES = List.of(
            SyncJobStatus.PENDING,
            SyncJobStatus.IN_PROGRESS
    );

    private final GitRepoRepository gitRepoRepository;
    private final SyncJobLogRepository jobRepository;
    private final SyncJobFinalizationService finalizationService;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public GitHubSyncJobService(
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository,
            SyncJobFinalizationService finalizationService,
            @Value("${app.integrations.sync-job-stale-after:PT15M}")
            Duration staleAfter
    ) {
        this(
                gitRepoRepository,
                jobRepository,
                finalizationService,
                staleAfter,
                Clock.systemUTC()
        );
    }

    GitHubSyncJobService(
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository,
            SyncJobFinalizationService finalizationService,
            Duration staleAfter,
            Clock clock
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.jobRepository = jobRepository;
        this.finalizationService = finalizationService;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobLog> claim(UUID repositoryId, SyncJobType jobType) {
        return claimOrReuse(repositoryId, jobType)
                .filter(result -> !result.coalesced())
                .map(SyncJobClaimResult::job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobClaimResult> claimOrReuse(
            UUID repositoryId,
            SyncJobType jobType
    ) {
        GitRepo repository = gitRepoRepository.findForInitialBackfillClaimById(
                repositoryId
        ).orElse(null);
        if (repository == null || repository.getConnectionStatus()
                == IntegrationStatus.DISCONNECTED) {
            return Optional.empty();
        }
        if (jobType == SyncJobType.INITIAL_BACKFILL
                && repository.getConnectionStatus() == IntegrationStatus.ACTIVE
                && repository.getLastSyncedAt() != null) {
            return Optional.empty();
        }

        List<SyncJobLog> activeJobs = jobRepository
                .findActiveByTargetIdOrderByStartedAtDesc(
                        repositoryId,
                        ACTIVE_STATUSES
                );
        if (!activeJobs.isEmpty()) {
            SyncJobLog activeJob = activeJobs.get(0);
            if (!isStale(activeJob, utcNow())) {
                return Optional.of(SyncJobClaimResult.coalesced(activeJob));
            }
            finalizationService.finalizeJob(
                    activeJob.getId(),
                    SyncJobStatus.FAILED,
                    valueOrZero(activeJob.getItemsProcessed()),
                    valueOrZero(activeJob.getItemsFailed()),
                    null,
                    "STALE_SYNC_JOB_RECOVERED",
                    "STALE_RECOVERY"
            );
        }

        if (jobType == SyncJobType.INITIAL_BACKFILL) {
            repository.setConnectionStatus(IntegrationStatus.BACKFILLING);
            gitRepoRepository.saveAndFlush(repository);
        }
        return Optional.of(SyncJobClaimResult.claimed(
                jobRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("GITHUB")
                .targetId(repositoryId)
                .jobType(jobType)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(utcNow())
                .cursorBefore(repository.getSyncCursor())
                .build())
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStaleJobs() {
        LocalDateTime now = utcNow();
        for (SyncJobLog job : jobRepository.findByStatusIn(ACTIVE_STATUSES)) {
            if (!"GITHUB".equals(job.getTargetSystem())
                    || !isStale(job, now)) {
                continue;
            }
            GitRepo repository = gitRepoRepository.findForStateUpdateById(
                    job.getTargetId()
            ).orElse(null);
            if (repository == null) {
                continue;
            }
            finalizationService.finalizeJob(
                    job.getId(),
                    SyncJobStatus.FAILED,
                    valueOrZero(job.getItemsProcessed()),
                    valueOrZero(job.getItemsFailed()),
                    null,
                    "STALE_SYNC_JOB_RECOVERED",
                    "STALE_RECOVERY"
            );
        }
    }

    private boolean isStale(SyncJobLog job, LocalDateTime now) {
        LocalDateTime startedAt = job.getStartedAt() == null
                ? job.getCreatedAt()
                : job.getStartedAt();
        return startedAt != null && !startedAt.isAfter(now.minus(staleAfter));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
