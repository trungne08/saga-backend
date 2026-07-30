package com.saga.be.integration.sync;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class JiraBoardStateServiceTest {

    @Test
    void retriesCompleteOnceAfterOptimisticLock() {
        JiraBoardStateWriteService writes = mock(JiraBoardStateWriteService.class);
        UUID boardId = UUID.randomUUID();
        LocalDateTime cursor = LocalDateTime.now();
        doThrow(new OptimisticLockingFailureException("conflict"))
                .doNothing()
                .when(writes).complete(boardId, cursor);

        new JiraBoardStateService(writes).complete(boardId, cursor);

        verify(writes, org.mockito.Mockito.times(2)).complete(boardId, cursor);
    }

    @Test
    void retriesDegradeOnceAfterOptimisticLock() {
        JiraBoardStateWriteService writes = mock(JiraBoardStateWriteService.class);
        UUID boardId = UUID.randomUUID();
        doThrow(new OptimisticLockingFailureException("conflict"))
                .doNothing()
                .when(writes).degrade(boardId);

        new JiraBoardStateService(writes).degrade(boardId);

        verify(writes, org.mockito.Mockito.times(2)).degrade(boardId);
    }
}
