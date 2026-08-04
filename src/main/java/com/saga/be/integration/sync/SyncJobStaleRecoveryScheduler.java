package com.saga.be.integration.sync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncJobStaleRecoveryScheduler {

    private final JiraSyncJobService jiraSyncJobService;
    private final GitHubSyncJobService gitHubSyncJobService;

    public SyncJobStaleRecoveryScheduler(
            JiraSyncJobService jiraSyncJobService,
            GitHubSyncJobService gitHubSyncJobService
    ) {
        this.jiraSyncJobService = jiraSyncJobService;
        this.gitHubSyncJobService = gitHubSyncJobService;
    }

    @Scheduled(fixedDelayString =
            "${app.integrations.stale-sync-job-recovery-delay-ms:60000}")
    public void recoverStaleJobs() {
        jiraSyncJobService.recoverStaleJobs();
        gitHubSyncJobService.recoverStaleJobs();
    }
}
