package com.saga.be.integration.sync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JiraStaleSyncJobRecoveryScheduler {

    private final JiraSyncJobService jiraSyncJobService;

    public JiraStaleSyncJobRecoveryScheduler(JiraSyncJobService jiraSyncJobService) {
        this.jiraSyncJobService = jiraSyncJobService;
    }

    @Scheduled(fixedDelayString =
            "${app.integrations.stale-sync-job-recovery-delay-ms:60000}")
    public void recover() {
        jiraSyncJobService.recoverStaleJobs();
    }
}
