package com.saga.be.dto.response;

import com.saga.be.entity.SyncJobLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record SyncHistoryResponse(UUID projectId, HistoryPage jobs) {

    public static SyncHistoryResponse from(UUID projectId, Page<SyncJobLog> jobs) {
        return new SyncHistoryResponse(projectId, new HistoryPage(
                jobs.getContent().stream().map(SyncStatusResponse.Job::from).toList(),
                jobs.getNumber(),
                jobs.getSize(),
                jobs.getTotalElements(),
                jobs.getTotalPages(),
                jobs.hasNext()
        ));
    }

    public record HistoryPage(
            List<SyncStatusResponse.Job> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) { }
}
