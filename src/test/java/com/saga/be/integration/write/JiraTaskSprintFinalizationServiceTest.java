package com.saga.be.integration.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JiraTaskSprintFinalizationServiceTest {

    @Test
    void appliesResolvedSprintToCanonicalTask() {
        TaskRepository tasks = mock(TaskRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        UUID projectId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Project project = Project.builder().build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).build();
        Sprint sprint = Sprint.builder().board(board).build(); sprint.setId(sprintId);
        Task task = Task.builder().project(project).externalId("101").build();
        when(tasks.findByProjectIdAndExternalId(projectId, "101")).thenReturn(Optional.of(task));
        when(sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintId, projectId)).thenReturn(Optional.of(sprint));

        new JiraTaskSprintFinalizationService(tasks, sprints).applyTarget(projectId, "101", sprintId);

        assertEquals(sprint, task.getSprint());
        verify(tasks).saveAndFlush(task);
    }

    @Test
    void clearsSprintForBacklogTarget() {
        TaskRepository tasks = mock(TaskRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().build(); project.setId(projectId);
        Task task = Task.builder().project(project).sprint(mock(Sprint.class)).externalId("101").build();
        when(tasks.findByProjectIdAndExternalId(projectId, "101")).thenReturn(Optional.of(task));

        new JiraTaskSprintFinalizationService(tasks, sprints).applyTarget(projectId, "101", null);

        assertNull(task.getSprint());
        verify(tasks).saveAndFlush(task);
    }

    @Test
    void missingCanonicalTaskStaysRecoveryRequiredWithoutWrite() {
        TaskRepository tasks = mock(TaskRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        UUID projectId = UUID.randomUUID();
        when(tasks.findByProjectIdAndExternalId(projectId, "101")).thenReturn(Optional.empty());

        IntegrationException error = assertThrows(IntegrationException.class,
                () -> new JiraTaskSprintFinalizationService(tasks, sprints).applyTarget(projectId, "101", UUID.randomUUID()));

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", error.getCode());
        verify(tasks, never()).saveAndFlush(any());
        verify(sprints, never()).findByIdAndBoardProjectIdAndDeletedAtIsNull(any(), eq(projectId));
    }
}
