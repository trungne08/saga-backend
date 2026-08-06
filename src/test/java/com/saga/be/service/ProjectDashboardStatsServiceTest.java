package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectDashboardStatsServiceTest {
    @Test
    void calculatesOnlyActiveDoneTasksAndLocalGitHubSnapshots() {
        ProjectDetailService projects = mock(ProjectDetailService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        GitRepoRepository repos = mock(GitRepoRepository.class);
        CommitDataRepository commits = mock(CommitDataRepository.class);
        PullRequestRepository prs = mock(PullRequestRepository.class);
        ProjectDashboardStatsService service = new ProjectDashboardStatsService(projects, tasks, repos, commits, prs);
        UUID projectId = UUID.randomUUID();
        Task done = Task.builder().status(TaskStatus.DONE).build();
        Task todo = Task.builder().status(TaskStatus.TODO).build();
        Task deleted = Task.builder().status(TaskStatus.DONE).deletedAt(java.time.LocalDateTime.now()).build();
        when(tasks.findByProjectId(projectId)).thenReturn(List.of(done, todo, deleted));
        when(repos.findByProjectIdOrderByFullName(projectId)).thenReturn(List.of(mock(com.saga.be.entity.GitRepo.class), mock(com.saga.be.entity.GitRepo.class)));
        when(commits.findByProjectId(projectId)).thenReturn(List.of(mock(com.saga.be.entity.CommitData.class)));
        when(prs.countByRepoProjectId(projectId)).thenReturn(3L);

        var result = service.get(mock(SagaPrincipal.class), projectId);

        assertEquals(2, result.tasks().total());
        assertEquals(1, result.tasks().completed());
        assertEquals(1, result.tasks().incomplete());
        assertEquals("50.00", result.tasks().completionPercentage().toPlainString());
        assertEquals(2, result.github().repositoryCount());
        assertEquals(1, result.github().commitCount());
        assertEquals(3, result.github().pullRequestCount());
        verify(projects).get(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(projectId));
    }

    @Test
    void returnsZeroPercentageForNoTasks() {
        ProjectDetailService projects = mock(ProjectDetailService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        GitRepoRepository repos = mock(GitRepoRepository.class);
        CommitDataRepository commits = mock(CommitDataRepository.class);
        PullRequestRepository prs = mock(PullRequestRepository.class);
        UUID projectId = UUID.randomUUID();
        when(tasks.findByProjectId(projectId)).thenReturn(List.of());
        when(repos.findByProjectIdOrderByFullName(projectId)).thenReturn(List.of());
        when(commits.findByProjectId(projectId)).thenReturn(List.of());
        when(prs.countByRepoProjectId(projectId)).thenReturn(0L);

        var result = new ProjectDashboardStatsService(projects, tasks, repos, commits, prs)
                .get(mock(SagaPrincipal.class), projectId);
        assertEquals("0", result.tasks().completionPercentage().toPlainString());
    }
}
