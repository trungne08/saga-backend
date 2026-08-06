package com.saga.be.integration.sync;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.JiraBoardRepository;
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
public class JiraSyncJobService {

    private static final List<SyncJobStatus> ACTIVE_STATUSES = List.of(
            SyncJobStatus.PENDING,
            SyncJobStatus.IN_PROGRESS
    );

    private final JiraBoardRepository boardRepository;
    private final SyncJobLogRepository jobRepository;
    private final SyncJobFinalizationService finalizationService;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public JiraSyncJobService(
            JiraBoardRepository boardRepository,
            SyncJobLogRepository jobRepository,
            SyncJobFinalizationService finalizationService,
            @Value("${app.integrations.sync-job-stale-after:PT15M}")
            Duration staleAfter
    ) {
        this(
                boardRepository,
                jobRepository,
                finalizationService,
                staleAfter,
                Clock.systemUTC()
        );
    }

    JiraSyncJobService(
            JiraBoardRepository boardRepository,
            SyncJobLogRepository jobRepository,
            SyncJobFinalizationService finalizationService,
            Duration staleAfter,
            Clock clock
    ) {
        this.boardRepository = boardRepository;
        this.jobRepository = jobRepository;
        this.finalizationService = finalizationService;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobLog> claim(UUID boardId, SyncJobType jobType) {
        return claimOrReuse(boardId, jobType)
                .filter(result -> !result.coalesced())
                .map(SyncJobClaimResult::job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncJobClaimResult> claimOrReuse(
            UUID boardId,
            SyncJobType jobType
    ) {
        JiraBoard board = boardRepository.findForSyncClaimById(boardId)
                .orElse(null);
        if (board == null || board.getConnectionStatus()
                == IntegrationStatus.DISCONNECTED) {
            return Optional.empty();
        }
        if (jobType == SyncJobType.INITIAL_BACKFILL
                && board.getConnectionStatus() == IntegrationStatus.ACTIVE
                && board.getLastSyncedAt() != null) {
            return Optional.empty();
        }

        List<SyncJobLog> activeJobs = jobRepository
                .findActiveByTargetIdOrderByStartedAtDesc(
                        boardId,
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
                    "STALE_SYNC_JOB_RECOVERED"
            );
        }

        if (jobType == SyncJobType.INITIAL_BACKFILL) {
            board.setConnectionStatus(IntegrationStatus.BACKFILLING);
            boardRepository.saveAndFlush(board);
        }
        return Optional.of(SyncJobClaimResult.claimed(
                jobRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("JIRA")
                .targetId(boardId)
                .jobType(jobType)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(utcNow())
                .cursorBefore(board.getSyncCursor())
                .build())
        ));
    }

    public void recoverStaleJobs() {
        LocalDateTime now = utcNow();
        for (SyncJobLog job : jobRepository.findByStatusIn(ACTIVE_STATUSES)) {
            if ("JIRA".equals(job.getTargetSystem()) && isStale(job, now)) {
                finalizationService.finalizeJob(
                        job.getId(),
                        SyncJobStatus.FAILED,
                        valueOrZero(job.getItemsProcessed()),
                        valueOrZero(job.getItemsFailed()),
                        null,
                        "STALE_SYNC_JOB_RECOVERED"
                );
            }
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
