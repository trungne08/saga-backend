package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JiraIssueUpsertServiceTest {

    private JiraBoardRepository boardRepository;
    private TaskRepository taskRepository;
    private IdentityMappingService mappingService;
    private JiraIssueUpsertService service;
    private UUID boardId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        boardRepository = mock(JiraBoardRepository.class);
        taskRepository = mock(TaskRepository.class);
        mappingService = mock(IdentityMappingService.class);
        service = new JiraIssueUpsertService(
                boardRepository,
                taskRepository,
                mock(SprintRepository.class),
                mappingService
        );
        boardId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).build();
        board.setId(boardId);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
    }

    @Test
    void repeatedIssueUsesStableProviderIdAndUpdatesSameTask() {
        Task existing = new Task();
        existing.setExternalId("jira-10001");
        when(taskRepository.findByProjectIdAndExternalId(
                projectId,
                "jira-10001"
        )).thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.upsert(boardId, snapshot(
                "jira-10001",
                "Done",
                LocalDateTime.parse("2026-07-29T10:00:00")
        )));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        Task saved = captor.getValue();
        assertSame(existing, saved);
        assertEquals("jira-10001", saved.getExternalId());
        assertEquals(TaskStatus.DONE, saved.getStatus());
        assertEquals(TaskType.BUG, saved.getType());
        assertEquals(Priority.HIGH, saved.getPriority());
    }

    @Test
    void staleJiraWebhookCannotOverwriteNewerTask() {
        Task existing = new Task();
        existing.setTitle("new title");
        existing.setExternalUpdatedAt(
                LocalDateTime.parse("2026-07-29T11:00:00")
        );
        when(taskRepository.findByProjectIdAndExternalId(
                projectId,
                "jira-10001"
        )).thenReturn(Optional.of(existing));

        assertFalse(service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:00:00")
        )));
        assertEquals("new title", existing.getTitle());
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameStableAtlassianIdPreservesHistoricalAttribution() {
        Student historicalStudent = new Student();
        Task existing = new Task();
        existing.setAssignee(historicalStudent);
        existing.setAssigneeExternalId("atlassian-id-1");
        when(taskRepository.findByProjectIdAndExternalId(
                projectId,
                "jira-10001"
        )).thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(boardId, snapshot(
                "jira-10001",
                "In Progress",
                LocalDateTime.parse("2026-07-29T10:00:00")
        ));

        assertSame(historicalStudent, existing.getAssignee());
        assertEquals("atlassian-id-1", existing.getAssigneeExternalId());
        verifyNoInteractions(mappingService);
    }

    private JiraIssueSnapshot snapshot(
            String id,
            String status,
            LocalDateTime updatedAt
    ) {
        return new JiraIssueSnapshot(
                id,
                "SAGA-1",
                "task title",
                "Bug",
                status,
                "High",
                3,
                "atlassian-id-1",
                null,
                null,
                LocalDateTime.parse("2026-07-28T10:00:00"),
                updatedAt,
                null,
                null,
                null,
                null
        );
    }
}
