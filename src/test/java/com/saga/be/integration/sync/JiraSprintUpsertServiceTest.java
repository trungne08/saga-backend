package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.integration.provider.JiraSprintSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class JiraSprintUpsertServiceTest {
    private JiraBoardRepository boards;
    private SprintRepository sprints;
    private JiraSprintUpsertService service;
    private UUID boardId;

    @BeforeEach
    void setUp() {
        boards = mock(JiraBoardRepository.class);
        sprints = mock(SprintRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service = new JiraSprintUpsertService(boards, sprints, transactions);
        boardId = UUID.randomUUID();
        JiraBoard board = JiraBoard.builder().project(Project.builder().build()).build(); board.setId(boardId);
        when(boards.findById(boardId)).thenReturn(Optional.of(board));
    }

    @Test
    void insertsCanonicalSprint() {
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42")).thenReturn(Optional.empty());
        when(sprints.saveAndFlush(any(Sprint.class))).thenAnswer(call -> call.getArgument(0));
        Sprint saved = service.upsert(boardId, snapshot("future"));
        assertEquals("42", saved.getExternalSprintId());
        assertEquals("future", saved.getState());
    }

    @Test
    void updatesExistingSprint() {
        Sprint existing = new Sprint(); existing.setName("old");
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42")).thenReturn(Optional.of(existing));
        when(sprints.saveAndFlush(existing)).thenReturn(existing);
        assertEquals("Sprint", service.upsert(boardId, snapshot("active")).getName());
        assertEquals("active", existing.getState());
    }

    @Test
    void duplicateTargetConstraintReloadsInNewTransaction() {
        Sprint raced = new Sprint();
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42"))
                .thenReturn(Optional.empty(), Optional.of(raced));
        when(sprints.saveAndFlush(any(Sprint.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_sprint_board_external"))
                .thenAnswer(call -> call.getArgument(0));
        assertEquals(raced, service.upsert(boardId, snapshot("future")));
        assertEquals("Sprint", raced.getName());
    }

    @Test
    void nonTargetConstraintPropagates() {
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42")).thenReturn(Optional.empty());
        when(sprints.saveAndFlush(any(Sprint.class))).thenThrow(new DataIntegrityViolationException("fk_other"));
        assertThrows(DataIntegrityViolationException.class, () -> service.upsert(boardId, snapshot("future")));
    }

    private JiraSprintSnapshot snapshot(String state) {
        return new JiraSprintSnapshot("42", "Sprint", state, "Goal", LocalDateTime.now(),
                LocalDateTime.now().plusDays(7), null, "7");
    }
}
