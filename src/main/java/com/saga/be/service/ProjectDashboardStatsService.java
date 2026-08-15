package com.saga.be.service;

import com.saga.be.dto.response.ProjectDashboardStatsResponse;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.SagaPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDashboardStatsService {
    private final ProjectDetailService projects;
    private final TaskRepository tasks;
    private final GitRepoRepository repositories;
    private final CommitDataRepository commits;
    private final PullRequestRepository pullRequests;

    public ProjectDashboardStatsService(ProjectDetailService projects, TaskRepository tasks,
            GitRepoRepository repositories, CommitDataRepository commits, PullRequestRepository pullRequests) {
        this.projects = projects; this.tasks = tasks; this.repositories = repositories;
        this.commits = commits; this.pullRequests = pullRequests;
    }

    @Transactional(readOnly = true)
    public ProjectDashboardStatsResponse get(SagaPrincipal principal, UUID projectId) {
        projects.get(principal, projectId); // shared Project read authorization; no provider I/O
        long total = 0;
        long done = 0;
        for (Task task : tasks.findByProjectId(projectId)) {
            if (task.getDeletedAt() == null) {
                total++;
                if (task.getStatus() == TaskStatus.DONE) done++;
            }
        }
        BigDecimal percentage = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(done).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ProjectDashboardStatsResponse(projectId, Instant.now(),
                new ProjectDashboardStatsResponse.Tasks(total, done, total - done, percentage),
                new ProjectDashboardStatsResponse.GitHub(
                        repositories.findByProjectIdOrderByFullName(projectId).size(),
                        commits.findByProjectId(projectId).size(),
                        pullRequests.countByRepoProjectId(projectId)));
    }
}
