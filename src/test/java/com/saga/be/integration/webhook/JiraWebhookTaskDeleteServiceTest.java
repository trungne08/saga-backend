package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraWebhookTaskDeleteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T04:05:06Z");

    private JiraBoardRepository boardRepository;
    private TaskRepository taskRepository;
    private JiraWebhookTaskDeleteService service;
    private UUID boardId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        boardRepository = mock(JiraBoardRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new JiraWebhookTaskDeleteService(
                boardRepository,
                taskRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        boardId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        Project project = Project.builder().name("SAGA").build();
        project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).build();
        board.setId(boardId);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
    }

    @Test
    void authenticatedBoardScopedDeleteCreatesTaskTombstone() {
        Task task = Task.builder().externalId("10001").externalKey("SAGA-1").build();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "10001"))
                .thenReturn(Optional.of(task));

        assertEquals(
                JiraWebhookTaskDeleteService.DeleteResult.TOMBSTONED,
                service.tombstone(boardId, "10001", "SAGA-1")
        );

        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), task.getDeletedAt());
        verify(taskRepository).saveAndFlush(task);
    }

    @Test
    void repeatedDeleteIsIdempotent() {
        LocalDateTime original = LocalDateTime.parse("2026-08-12T01:02:03");
        Task task = Task.builder()
                .externalId("10001")
                .externalKey("SAGA-1")
                .deletedAt(original)
                .build();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "10001"))
                .thenReturn(Optional.of(task));

        assertEquals(
                JiraWebhookTaskDeleteService.DeleteResult.ALREADY_TOMBSTONED,
                service.tombstone(boardId, "10001", "SAGA-1")
        );

        assertSame(original, task.getDeletedAt());
        verify(taskRepository, never()).saveAndFlush(task);
    }

    @Test
    void unknownTaskIsControlledNoOp() {
        when(taskRepository.findByProjectIdAndExternalId(projectId, "99999"))
                .thenReturn(Optional.empty());

        assertEquals(
                JiraWebhookTaskDeleteService.DeleteResult.UNKNOWN_TASK,
                service.tombstone(boardId, "99999", "SAGA-404")
        );

        verify(taskRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteCannotReachTaskOwnedByAnotherProject() {
        UUID otherProjectId = UUID.randomUUID();
        Task otherProjectTask = Task.builder()
                .externalId("10001")
                .externalKey("OTHER-1")
                .build();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "10001"))
                .thenReturn(Optional.empty());
        when(taskRepository.findByProjectIdAndExternalId(otherProjectId, "10001"))
                .thenReturn(Optional.of(otherProjectTask));

        assertEquals(
                JiraWebhookTaskDeleteService.DeleteResult.UNKNOWN_TASK,
                service.tombstone(boardId, "10001", "OTHER-1")
        );

        assertNull(otherProjectTask.getDeletedAt());
        verify(taskRepository, never()).findByProjectIdAndExternalId(otherProjectId, "10001");
        verify(taskRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keyIsUsedOnlyWhenStableIssueIdIsUnavailable() {
        Task task = Task.builder().externalKey("SAGA-1").build();
        when(taskRepository.findByProjectIdAndExternalKey(projectId, "SAGA-1"))
                .thenReturn(Optional.of(task));

        assertEquals(
                JiraWebhookTaskDeleteService.DeleteResult.TOMBSTONED,
                service.tombstone(boardId, null, "SAGA-1")
        );

        verify(taskRepository).saveAndFlush(task);
    }
}
