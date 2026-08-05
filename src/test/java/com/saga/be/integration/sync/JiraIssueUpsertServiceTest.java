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
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.value.TaskComponentSnapshot;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.service.TeamContributionRefreshService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class JiraIssueUpsertServiceTest {

    private JiraBoardRepository boardRepository;
    private TaskRepository taskRepository;
    private IdentityMappingService mappingService;
    private TeamContributionRefreshService teamContributionRefreshService;
    private SprintRepository sprintRepository;
    private JiraIssueUpsertService service;
    private UUID boardId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        boardRepository = mock(JiraBoardRepository.class);
        taskRepository = mock(TaskRepository.class);
        mappingService = mock(IdentityMappingService.class);
        teamContributionRefreshService = mock(TeamContributionRefreshService.class);
        sprintRepository = mock(SprintRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service = new JiraIssueUpsertService(
                boardRepository,
                taskRepository,
                sprintRepository,
                mappingService,
                teamContributionRefreshService,
                transactionManager
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

    @Test
    void newTaskStoresEveryLabelFromTheSnapshot() {
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:00:00"),
                List.of("Backend", "Sprint 1")
        ));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        assertEquals(List.of("Backend", "Sprint 1"), captor.getValue().getLabels());
    }

    @Test
    void existingTaskReplacesLabelsAndClearsThemForAnEmptySnapshot() {
        Task existing = new Task();
        existing.setLabels(List.of("obsolete"));
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:00:00"),
                List.of("new-one", "new-two")
        ));
        assertEquals(List.of("new-one", "new-two"), existing.getLabels());

        existing.setLabels(List.of("prior-one", "prior-two"));
        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:30:00"),
                List.of("replacement")
        ));
        assertEquals(List.of("replacement"), existing.getLabels());

        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T11:00:00"),
                List.of()
        ));
        assertEquals(List.of(), existing.getLabels());
        assertEquals("jira-10001", existing.getExternalId());
        assertEquals("SAGA-1", existing.getExternalKey());
    }

    @Test
    void repeatedSnapshotDoesNotDuplicateLabels() {
        Task existing = new Task();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        JiraIssueSnapshot snapshot = snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:00:00"),
                List.of("stable", "stable-again")
        );

        service.upsert(boardId, snapshot);
        service.upsert(boardId, snapshot);

        assertEquals(List.of("stable", "stable-again"), existing.getLabels());
    }

    @Test
    void existingTaskReplacesDescriptionAndComponentsAndClearsComponents() {
        Task existing = new Task();
        existing.setDescription("obsolete description");
        existing.setComponents(List.of(new TaskComponentSnapshot("1", "obsolete")));
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T10:00:00"),
                List.of(),
                "canonical description",
                List.of(new TaskComponentSnapshot("10", "Backend"))
        ));
        assertEquals("canonical description", existing.getDescription());
        assertEquals(List.of(new TaskComponentSnapshot("10", "Backend")), existing.getComponents());

        service.upsert(boardId, snapshot(
                "jira-10001",
                "To Do",
                LocalDateTime.parse("2026-07-29T11:00:00"),
                List.of(),
                null,
                List.of()
        ));
        assertEquals(null, existing.getDescription());
        assertEquals(List.of(), existing.getComponents());
        assertEquals("jira-10001", existing.getExternalId());
        assertEquals("SAGA-1", existing.getExternalKey());
    }

    @Test
    void duplicateInsertFromConcurrentReconciliationReloadsAndAppliesCanonicalSnapshot() {
        Task raced = new Task();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.empty(), Optional.of(raced));
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key uk_task_project_external"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.upsert(boardId, snapshot(
                "jira-10001", "Done", LocalDateTime.parse("2026-07-29T12:00:00")
        )));

        assertEquals("jira-10001", raced.getExternalId());
        assertEquals(TaskStatus.DONE, raced.getStatus());
        verify(taskRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Task.class));
    }

    @Test
    void partialIssueSprintAssociationNeverClearsCanonicalDates() {
        LocalDateTime start = LocalDateTime.parse("2026-08-01T02:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-08-15T02:00:00");
        Sprint canonical = Sprint.builder()
                .externalSprintId("42")
                .name("Canonical name")
                .startDate(start)
                .endDate(end)
                .build();
        Task task = new Task();
        when(taskRepository.findByProjectIdAndExternalId(projectId, "jira-10001"))
                .thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);
        when(sprintRepository.findByBoardIdAndExternalSprintId(boardId, "42"))
                .thenReturn(Optional.of(canonical));
        when(sprintRepository.save(canonical)).thenReturn(canonical);
        LocalDateTime updatedAt = LocalDateTime.parse("2026-08-04T05:00:00");
        JiraIssueSnapshot partial = new JiraIssueSnapshot(
                "jira-10001", "SAGA-1", "task title", "Task", "To Do",
                "Medium", null, null, null, null, updatedAt.minusDays(1),
                updatedAt, null, null, "42", "Embedded name"
        );

        service.upsert(boardId, partial);

        assertSame(canonical, task.getSprint());
        assertEquals("Embedded name", canonical.getName());
        assertEquals(start, canonical.getStartDate());
        assertEquals(end, canonical.getEndDate());
    }

    private JiraIssueSnapshot snapshot(
            String id,
            String status,
            LocalDateTime updatedAt
    ) {
        return snapshot(id, status, updatedAt, List.of());
    }

    private JiraIssueSnapshot snapshot(
            String id,
            String status,
            LocalDateTime updatedAt,
            List<String> labels
    ) {
        return snapshot(id, status, updatedAt, labels, null, List.of());
    }

    private JiraIssueSnapshot snapshot(
            String id,
            String status,
            LocalDateTime updatedAt,
            List<String> labels,
            String description,
            List<TaskComponentSnapshot> components
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
                null,
                updatedAt.toInstant(java.time.ZoneOffset.UTC),
                labels,
                description,
                components
        );
    }
}
