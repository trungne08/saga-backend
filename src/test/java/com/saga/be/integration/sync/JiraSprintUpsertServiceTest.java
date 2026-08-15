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
    void fullCanonicalNullDatesClearPreviouslyStoredDates() {
        Sprint existing = Sprint.builder()
                .startDate(LocalDateTime.parse("2026-08-01T02:00:00"))
                .endDate(LocalDateTime.parse("2026-08-15T02:00:00"))
                .completeDate(LocalDateTime.parse("2026-08-15T03:00:00"))
                .build();
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42"))
                .thenReturn(Optional.of(existing));
        when(sprints.saveAndFlush(existing)).thenReturn(existing);
        JiraSprintSnapshot canonical = new JiraSprintSnapshot(
                "42", "Sprint", "future", null, null, null, null, "7"
        );

        service.upsert(boardId, canonical);

        assertEquals(null, existing.getStartDate());
        assertEquals(null, existing.getEndDate());
        assertEquals(null, existing.getCompleteDate());
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
        assertEquals(
                LocalDateTime.parse("2026-08-01T02:00:00"),
                raced.getStartDate()
        );
    }

    @Test
    void nonTargetConstraintPropagates() {
        when(sprints.findByBoardIdAndExternalSprintId(boardId, "42")).thenReturn(Optional.empty());
        when(sprints.saveAndFlush(any(Sprint.class))).thenThrow(new DataIntegrityViolationException("fk_other"));
        assertThrows(DataIntegrityViolationException.class, () -> service.upsert(boardId, snapshot("future")));
    }

    private JiraSprintSnapshot snapshot(String state) {
        return new JiraSprintSnapshot(
                "42",
                "Sprint",
                state,
                "Goal",
                LocalDateTime.parse("2026-08-01T02:00:00"),
                LocalDateTime.parse("2026-08-08T02:00:00"),
                null,
                "7"
        );
    }
}
