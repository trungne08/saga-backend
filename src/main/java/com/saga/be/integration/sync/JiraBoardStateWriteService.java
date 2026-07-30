package com.saga.be.integration.sync;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.repository.JiraBoardRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes board state from a fresh persistence context, never from a worker entity. */
@Service
public class JiraBoardStateWriteService {

    private final JiraBoardRepository boardRepository;

    public JiraBoardStateWriteService(JiraBoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID boardId, LocalDateTime cursor) {
        JiraBoard board = latestBoard(boardId);
        board.setSyncCursor(cursor);
        board.setLastSyncedAt(LocalDateTime.now());
        board.setConnectionStatus(IntegrationStatus.ACTIVE);
        board.setConsecutiveFailures(0);
        boardRepository.saveAndFlush(board);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void degrade(UUID boardId) {
        JiraBoard board = latestBoard(boardId);
        board.setConsecutiveFailures(board.getConsecutiveFailures() + 1);
        board.setConnectionStatus(IntegrationStatus.DEGRADED);
        boardRepository.saveAndFlush(board);
    }

    private JiraBoard latestBoard(UUID boardId) {
        return boardRepository.findForSyncClaimById(boardId)
                .orElseThrow(() -> new IllegalStateException(
                        "Jira board is no longer available"
                ));
    }
}
