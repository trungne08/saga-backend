package com.saga.be.integration.sync;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** Retries only the local board-state write once; it never repeats Jira API calls. */
@Service
public class JiraBoardStateService {

    private final JiraBoardStateWriteService writeService;

    public JiraBoardStateService(JiraBoardStateWriteService writeService) {
        this.writeService = writeService;
    }

    public void complete(UUID boardId, LocalDateTime cursor) {
        retryOnce(() -> writeService.complete(boardId, cursor));
    }

    public void degrade(UUID boardId) {
        retryOnce(() -> writeService.degrade(boardId));
    }

    private void retryOnce(Runnable write) {
        try {
            write.run();
        } catch (OptimisticLockingFailureException firstFailure) {
            write.run();
        }
    }
}
