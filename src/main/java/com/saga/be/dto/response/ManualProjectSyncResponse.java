package com.saga.be.dto.response;

import com.saga.be.dto.request.ManualSyncProvider;
import com.saga.be.entity.enums.SyncJobStatus;
import java.util.List;
import java.util.UUID;

public record ManualProjectSyncResponse(
        UUID projectId,
        ManualSyncProvider requestedProvider,
        boolean accepted,
        List<Target> targets
) {
    public record Target(
            UUID jobId,
            String targetSystem,
            SyncJobStatus status,
            boolean coalesced
    ) {
    }
}
