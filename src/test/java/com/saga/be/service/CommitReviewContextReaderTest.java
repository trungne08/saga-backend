package com.saga.be.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitIssueCommitLink;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskGitIssueLink;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.entity.enums.TraceabilityRelationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueCommitLinkRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TaskGitIssueLinkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class CommitReviewContextReaderTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REPOSITORY_ID = UUID.randomUUID();
    private static final UUID COMMIT_ID = UUID.randomUUID();
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private ProjectRepository projects;
    private GitRepoRepository repositories;
    private CommitDataRepository commits;
    private GitIssueCommitLinkRepository issueCommitLinks;
    private TaskGitIssueLinkRepository taskIssueLinks;
    private CommitReviewContextReader reader;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        repositories = mock(GitRepoRepository.class);
        commits = mock(CommitDataRepository.class);
        issueCommitLinks = mock(GitIssueCommitLinkRepository.class);
        taskIssueLinks = mock(TaskGitIssueLinkRepository.class);
        reader = new CommitReviewContextReader(
                projects,
                repositories,
                commits,
                issueCommitLinks,
                taskIssueLinks
        );
    }

    @Test
    void missingProjectFailsBeforeRepositoryResolution() {
        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> reader.load(PROJECT_ID, 42L, SHA)
        );

        assertEquals("SAGA_PROJECT_NOT_FOUND", failure.getCode());
        verify(repositories, never())
                .findForCommitContextByProjectIdAndRepositoryId(PROJECT_ID, 42L);
    }

    @Test
    void repositoryOutsideRequestedProjectFailsClosed() {
        when(projects.existsById(PROJECT_ID)).thenReturn(true);

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> reader.load(PROJECT_ID, 42L, SHA)
        );

        assertEquals("GITHUB_REPOSITORY_NOT_FOUND", failure.getCode());
        verify(commits, never()).findByRepoIdAndShaHash(REPOSITORY_ID, SHA);
    }

    @Test
    void commitOutsideResolvedRepositoryFailsClosed() {
        GitRepo repository = repository(PROJECT_ID);
        when(projects.existsById(PROJECT_ID)).thenReturn(true);
        when(repositories.findForCommitContextByProjectIdAndRepositoryId(PROJECT_ID, 42L))
                .thenReturn(Optional.of(repository));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> reader.load(PROJECT_ID, 42L, SHA)
        );

        assertEquals("GITHUB_COMMIT_NOT_FOUND", failure.getCode());
    }

    @Test
    void explicitCommitIssueAndIssueTaskLinksAreReturnedWithTheirLinkIds() {
        GitRepo repository = repository(PROJECT_ID);
        CommitData commit = commit(repository);
        GitIssue issue = issue(repository, "Not inferred from commit text");
        Task task = task(PROJECT_ID, "SAGA-42", "Explicit requirement");
        GitIssueCommitLink issueLink = issueCommitLink(issue, commit);
        TaskGitIssueLink taskLink = taskIssueLink(issue, task);
        stubSource(repository, commit, List.of(issueLink), List.of(taskLink));

        CommitReviewContextReader.SourceSnapshot result =
                reader.load(PROJECT_ID, 42L, SHA);

        assertThat(result.linkedIssues()).hasSize(1);
        assertEquals(issueLink.getId(), result.linkedIssues().get(0).issueCommitLinkId());
        assertEquals(issue.getId(), result.linkedIssues().get(0).issueId());
        assertThat(result.linkedIssues().get(0).linkedTasks()).hasSize(1);
        assertEquals(
                taskLink.getId(),
                result.linkedIssues().get(0).linkedTasks().get(0).taskIssueLinkId()
        );
        assertEquals(task.getId(), result.linkedIssues().get(0)
                .linkedTasks().get(0).taskId());
    }

    @Test
    void legacyDirectFieldsAndTextCoincidenceNeverCreateTraceability() {
        GitRepo repository = repository(PROJECT_ID);
        GitIssue coincidentalIssue = issue(repository, "SAGA-42");
        Task coincidentalTask = task(PROJECT_ID, "SAGA-42", "Fix #42");
        CommitData commit = commit(repository);
        commit.setMessage("Fix #42 for SAGA-42");
        commit.setGitIssue(coincidentalIssue);
        commit.setTask(coincidentalTask);
        stubSource(repository, commit, List.of(), List.of());

        CommitReviewContextReader.SourceSnapshot result =
                reader.load(PROJECT_ID, 42L, SHA);

        assertThat(result.linkedIssues()).isEmpty();
        verify(taskIssueLinks, never())
                .findByGitIssueIdInOrderByTaskExternalKeyAscIdAsc(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.any(Pageable.class)
                );
    }

    @Test
    void normalizedRelationCrossingProjectBoundaryIsRejected() {
        GitRepo repository = repository(PROJECT_ID);
        CommitData commit = commit(repository);
        GitRepo otherRepository = repository(UUID.randomUUID());
        GitIssue crossProjectIssue = issue(otherRepository, "Cross-project issue");
        GitIssueCommitLink issueLink = issueCommitLink(crossProjectIssue, commit);
        stubSource(repository, commit, List.of(issueLink), List.of());

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> reader.load(PROJECT_ID, 42L, SHA)
        );

        assertEquals("TRACEABILITY_PROJECT_MISMATCH", failure.getCode());
    }

    private void stubSource(
            GitRepo repository,
            CommitData commit,
            List<GitIssueCommitLink> issueLinks,
            List<TaskGitIssueLink> taskLinks
    ) {
        when(projects.existsById(PROJECT_ID)).thenReturn(true);
        when(repositories.findForCommitContextByProjectIdAndRepositoryId(PROJECT_ID, 42L))
                .thenReturn(Optional.of(repository));
        when(commits.findByRepoIdAndShaHash(repository.getId(), SHA))
                .thenReturn(Optional.of(commit));
        when(issueCommitLinks.findByCommitIdOrderByGitIssueIssueNumberAscIdAsc(
                org.mockito.ArgumentMatchers.eq(COMMIT_ID),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(issueLinks);
        if (!issueLinks.isEmpty()) {
            when(taskIssueLinks.findByGitIssueIdInOrderByTaskExternalKeyAscIdAsc(
                    org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.any(Pageable.class)
            )).thenReturn(taskLinks);
        }
    }

    private GitRepo repository(UUID projectId) {
        Project project = Project.builder().name("Project").build();
        project.setId(projectId);
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(9001L)
                .build();
        GitRepo repository = GitRepo.builder()
                .project(project)
                .provider("GITHUB")
                .repositoryId(42L)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .installation(installation)
                .build();
        repository.setId(REPOSITORY_ID);
        return repository;
    }

    private CommitData commit(GitRepo repository) {
        CommitData commit = CommitData.builder()
                .repo(repository)
                .shaHash(SHA)
                .message("Commit message")
                .filesChanged(1)
                .build();
        commit.setId(COMMIT_ID);
        return commit;
    }

    private GitIssue issue(GitRepo repository, String title) {
        GitIssue issue = GitIssue.builder()
                .repo(repository)
                .issueNumber(42)
                .title(title)
                .state(IssueState.OPEN)
                .build();
        issue.setId(UUID.randomUUID());
        return issue;
    }

    private Task task(UUID projectId, String externalKey, String description) {
        Project project = Project.builder().name("Project").build();
        project.setId(projectId);
        Task task = Task.builder()
                .project(project)
                .externalKey(externalKey)
                .title("Task title")
                .description(description)
                .build();
        task.setId(UUID.randomUUID());
        return task;
    }

    private GitIssueCommitLink issueCommitLink(GitIssue issue, CommitData commit) {
        GitIssueCommitLink link = GitIssueCommitLink.builder()
                .gitIssue(issue)
                .commit(commit)
                .relationType(TraceabilityRelationType.REFERENCE)
                .build();
        link.setId(UUID.randomUUID());
        return link;
    }

    private TaskGitIssueLink taskIssueLink(GitIssue issue, Task task) {
        TaskGitIssueLink link = TaskGitIssueLink.builder()
                .gitIssue(issue)
                .task(task)
                .build();
        link.setId(UUID.randomUUID());
        return link;
    }
}
