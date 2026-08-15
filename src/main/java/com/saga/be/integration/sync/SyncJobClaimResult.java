package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;

public record SyncJobClaimResult(SyncJobLog job, boolean coalesced) {

    public static SyncJobClaimResult claimed(SyncJobLog job) {
        return new SyncJobClaimResult(job, false);
    }

    public static SyncJobClaimResult coalesced(SyncJobLog job) {
        return new SyncJobClaimResult(job, true);
    }
}
