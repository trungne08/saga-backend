package com.saga.be.integration.sync;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.repository.JiraBoardRepository;
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

    @Test
    void stateWritesNeverResurrectADisconnectedBoard() {
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        UUID boardId = UUID.randomUUID();
        JiraBoard board = JiraBoard.builder()
                .connectionStatus(IntegrationStatus.DISCONNECTED).build();
        when(boards.findForSyncClaimById(boardId)).thenReturn(Optional.of(board));
        JiraBoardStateWriteService writes = new JiraBoardStateWriteService(boards);

        writes.complete(boardId, LocalDateTime.now());
        writes.degrade(boardId);

        verify(boards, never()).saveAndFlush(board);
    }
}
