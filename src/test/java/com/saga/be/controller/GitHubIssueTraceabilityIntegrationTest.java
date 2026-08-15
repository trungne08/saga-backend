package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitIssueCommitLink;
import com.saga.be.entity.GitIssuePullRequestLink;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.PullRequest;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskGitIssueLink;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.entity.enums.PullRequestStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TraceabilityRelationType;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.GitIssueCommitLinkRepository;
import com.saga.be.repository.GitIssuePullRequestLinkRepository;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TaskGitIssueLinkRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class GitHubIssueTraceabilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturers;
    @Autowired private CourseRepository courses;
    @Autowired private ProjectRepository projects;
    @Autowired private TeamRepository teams;
    @Autowired private StudentRepository students;
    @Autowired private TeamMemberRepository teamMembers;
    @Autowired private GitRepoRepository gitRepos;
    @Autowired private GitIssueRepository gitIssues;
    @Autowired private TaskRepository tasks;
    @Autowired private PullRequestRepository pullRequests;
    @Autowired private CommitDataRepository commits;
    @Autowired private TaskGitIssueLinkRepository taskIssueLinks;
    @Autowired private GitIssuePullRequestLinkRepository issuePullLinks;
    @Autowired private GitIssueCommitLinkRepository issueCommitLinks;

    @MockitoBean private GitHubProviderClient githubProvider;
    @MockitoBean private JiraProviderClient jiraProvider;
    @MockitoBean private AuthenticationAuditService auditService;

    @AfterEach
    void noProviderReadOrWriteWasCalled() {
        verifyNoInteractions(githubProvider, jiraProvider);
    }

    @Test
    void issueListSupportsFiltersPaginationCountersAndSafeUnresolvedIdentity() throws Exception {
        Fixture f = fixture("LIST");
        GitRepo secondRepo = repo(f.project(), 202L, "saga/frontend");
        issue(f.repo(), 11, "Open backend issue", IssueState.OPEN, f.leader(), null,
                LocalDateTime.parse("2026-08-11T10:00:00"));
        issue(f.repo(), 12, "Closed backend issue", IssueState.CLOSED, null, null,
                LocalDateTime.parse("2026-08-11T09:00:00"));
        GitIssue unresolved = issue(secondRepo, 13, "Open unresolved author", IssueState.OPEN,
                null, null, LocalDateTime.parse("2026-08-11T08:00:00"));
        unresolved.setAuthorExternalId("999999");
        unresolved.setAssigneeExternalId("888888");
        gitIssues.saveAndFlush(unresolved);

        Authentication member = auth(ApplicationRole.STUDENT, f.member().getId());
        mockMvc.perform(get("/api/projects/{projectId}/github/issues", f.project().getId())
                        .param("state", "OPEN")
                        .param("keyword", "open")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary.open").value(2))
                .andExpect(jsonPath("$.summary.closed").value(1))
                .andExpect(jsonPath("$.summary.unassigned").value(3))
                .andExpect(jsonPath("$.content[0].githubIssueId").doesNotExist())
                .andExpect(jsonPath("$.content[0].nodeId").doesNotExist())
                .andExpect(jsonPath("$.content[0].authorExternalId").doesNotExist());

        mockMvc.perform(get("/api/projects/{projectId}/github/issues", f.project().getId())
                        .param("repositoryId", "202")
                        .param("keyword", "#13")
                        .with(authentication(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].issueNumber").value(13))
                .andExpect(jsonPath("$.content[0].author").doesNotExist())
                .andExpect(jsonPath("$.content[0].assignee").doesNotExist());
    }

    @Test
    void assignedToMeUsesResolvedStudentAndRejectsNonStudentActor() throws Exception {
        Fixture f = fixture("MINE");
        issue(f.repo(), 21, "Mine", IssueState.OPEN, null, f.leader(), LocalDateTime.now());
        issue(f.repo(), 22, "Not mine", IssueState.OPEN, null, f.member(), LocalDateTime.now());

        mockMvc.perform(get("/api/projects/{projectId}/github/issues", f.project().getId())
                        .param("assignedToMe", "true")
                        .with(authentication(auth(ApplicationRole.STUDENT, f.leader().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.summary.assignedToMe").value(1))
                .andExpect(jsonPath("$.content[0].issueNumber").value(21));

        mockMvc.perform(get("/api/projects/{projectId}/github/issues", f.project().getId())
                        .param("assignedToMe", "true")
                        .with(authentication(auth(ApplicationRole.LECTURER, f.lecturer().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GITHUB_ASSIGNED_TO_ME_UNAVAILABLE"));
    }

    @Test
    void issueDetailAndTaskTraceabilityUseNormalizedManyToManyLinksAndBoundedTimeline()
            throws Exception {
        Fixture f = fixture("DETAIL");
        Task task = task(f.project(), "SAGA-41", "Plan implementation",
                LocalDateTime.parse("2026-08-11T07:00:00"));
        Task secondTask = task(f.project(), "SAGA-40", "Prepare implementation",
                LocalDateTime.parse("2026-08-11T06:00:00"));
        GitIssue issue = issue(f.repo(), 41, "Track implementation", IssueState.OPEN,
                f.leader(), f.member(), LocalDateTime.parse("2026-08-11T08:00:00"));
        GitIssue secondIssue = issue(f.repo(), 40, "Track prerequisite", IssueState.CLOSED,
                f.leader(), null, LocalDateTime.parse("2026-08-11T05:00:00"));
        PullRequest pull = pull(f.repo(), 42, "Implement traceability",
                LocalDateTime.parse("2026-08-11T09:00:00"));
        CommitData commit = commit(f.repo(), "abcdef123", "Implement link tables",
                LocalDateTime.parse("2026-08-11T10:00:00"));
        taskIssueLinks.saveAndFlush(TaskGitIssueLink.builder().task(task).gitIssue(issue).build());
        taskIssueLinks.saveAndFlush(TaskGitIssueLink.builder().task(secondTask).gitIssue(issue).build());
        taskIssueLinks.saveAndFlush(TaskGitIssueLink.builder().task(task).gitIssue(secondIssue).build());
        issuePullLinks.saveAndFlush(GitIssuePullRequestLink.builder()
                .gitIssue(issue).pullRequest(pull)
                .relationType(TraceabilityRelationType.CLOSING_REFERENCE).build());
        issueCommitLinks.saveAndFlush(GitIssueCommitLink.builder()
                .gitIssue(issue).commit(commit)
                .relationType(TraceabilityRelationType.REFERENCE).build());

        Authentication member = auth(ApplicationRole.STUDENT, f.member().getId());
        mockMvc.perform(get("/api/projects/{projectId}/github/issues/{issueId}",
                        f.project().getId(), issue.getId()).with(authentication(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedTasks.items.length()").value(2))
                .andExpect(jsonPath("$.linkedTasks.items[0].externalKey").value("SAGA-40"))
                .andExpect(jsonPath("$.linkedPullRequests.items[0].pullNumber").value(42))
                .andExpect(jsonPath("$.linkedPullRequests.items[0].relationType")
                        .value("CLOSING_REFERENCE"))
                .andExpect(jsonPath("$.linkedCommits.items[0].sha").value("abcdef123"))
                .andExpect(jsonPath("$.timeline[0].sourceType").value("GITHUB_COMMIT"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/{taskId}/traceability",
                        f.project().getId(), task.getId()).with(authentication(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedIssues.items.length()").value(2))
                .andExpect(jsonPath("$.linkedIssues.items[1].issue.issueNumber").value(41))
                .andExpect(jsonPath("$.linkedIssues.items[1].linkedPullRequests.items[0].pullNumber")
                        .value(42))
                .andExpect(jsonPath("$.linkedIssues.items[1].linkedCommits.items[0].sha")
                        .value("abcdef123"));

        mockMvc.perform(get("/api/projects/{projectId}/traceability", f.project().getId())
                        .param("limit", "2")
                        .with(authentication(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.timeline[0].sourceType").value("GITHUB_COMMIT"));
    }

    @Test
    void linkIsIdempotentAndRepeatedUnlinkIsNoContentForOwningLeader() throws Exception {
        Fixture f = fixture("LINK");
        Task task = task(f.project(), "SAGA-51", "Link task", LocalDateTime.now());
        GitIssue issue = issue(f.repo(), 51, "Link issue", IssueState.OPEN,
                null, null, LocalDateTime.now());
        Authentication leader = auth(ApplicationRole.STUDENT, f.leader().getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}",
                            f.project().getId(), task.getId(), issue.getId())
                            .with(authentication(leader)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linked").value(true));
        }
        assertEquals(1, taskIssueLinks.count());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}",
                            f.project().getId(), task.getId(), issue.getId())
                            .with(authentication(leader)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertEquals(0, taskIssueLinks.count());
    }

    @Test
    void linkAuthorizationReusesManagerRuleAndRequiresCsrf() throws Exception {
        Fixture f = fixture("AUTH");
        Task task = task(f.project(), "SAGA-61", "Authorized task", LocalDateTime.now());
        GitIssue issue = issue(f.repo(), 61, "Authorized issue", IssueState.OPEN,
                null, null, LocalDateTime.now());
        String path = "/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}";

        mockMvc.perform(post(path, f.project().getId(), task.getId(), issue.getId())
                        .with(authentication(auth(ApplicationRole.STUDENT, f.leader().getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, f.project().getId(), task.getId(), issue.getId())
                        .with(authentication(auth(ApplicationRole.STUDENT, f.member().getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        Lecturer outsider = lecturer("OUTSIDER");
        mockMvc.perform(post(path, f.project().getId(), task.getId(), issue.getId())
                        .with(authentication(auth(ApplicationRole.LECTURER, outsider.getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, f.project().getId(), task.getId(), issue.getId())
                        .with(authentication(auth(ApplicationRole.LECTURER, f.lecturer().getId())))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(path, f.project().getId(), task.getId(), issue.getId())
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void linkRejectsCrossProjectAndUnknownResourcesDeterministically() throws Exception {
        Fixture f = fixture("SCOPE-A");
        Fixture other = fixture("SCOPE-B");
        Task task = task(f.project(), "SAGA-71", "Scoped task", LocalDateTime.now());
        GitIssue issue = issue(f.repo(), 71, "Scoped issue", IssueState.OPEN,
                null, null, LocalDateTime.now());
        GitIssue crossProject = issue(other.repo(), 72, "Other issue", IssueState.OPEN,
                null, null, LocalDateTime.now());
        Authentication admin = auth(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}",
                        f.project().getId(), task.getId(), crossProject.getId())
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TRACEABILITY_PROJECT_MISMATCH"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}",
                        f.project().getId(), UUID.randomUUID(), issue.getId())
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TASK_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}",
                        f.project().getId(), task.getId(), UUID.randomUUID())
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GITHUB_ISSUE_NOT_FOUND"));
        mockMvc.perform(get("/api/projects/{projectId}/github/issues/{issueId}",
                        f.project().getId(), crossProject.getId())
                        .with(authentication(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GITHUB_ISSUE_NOT_FOUND"));
        mockMvc.perform(get("/api/projects/{projectId}/github/issues", f.project().getId())
                        .param("repositoryId", Long.toString(other.repo().getRepositoryId()))
                        .with(authentication(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GITHUB_REPOSITORY_NOT_FOUND"));
    }

    @Test
    void commitLinkIsIdempotentManualOnlyAndRepeatedUnlinkIsNoContent() throws Exception {
        Fixture f = fixture("COMMIT-LINK");
        GitIssue issue = issue(f.repo(), 81, "Manual commit link", IssueState.OPEN,
                null, null, LocalDateTime.now());
        CommitData commit = commit(f.repo(), "fedcba9876543210fedcba9876543210fedcba98",
                "Fixes SAGA-81 #81", LocalDateTime.now());
        Authentication leader = auth(ApplicationRole.STUDENT, f.leader().getId());
        String path = "/api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}";

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(path, f.project().getId(), issue.getId(), commit.getId())
                            .with(authentication(leader)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linked").value(true));
        }
        assertEquals(1, issueCommitLinks.count());
        assertEquals(TraceabilityRelationType.MANUAL, issueCommitLinks.findAll().get(0).getRelationType());

        mockMvc.perform(get("/api/projects/{projectId}/github/issues/{issueId}",
                        f.project().getId(), issue.getId()).with(authentication(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCommits.items[0].sha")
                        .value("fedcba9876543210fedcba9876543210fedcba98"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(path, f.project().getId(), issue.getId(), commit.getId())
                            .with(authentication(leader)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertEquals(0, issueCommitLinks.count());
    }

    @Test
    void commitLinkRejectsCrossProjectMemberAndMissingCsrf() throws Exception {
        Fixture f = fixture("COMMIT-AUTH");
        Fixture other = fixture("COMMIT-OTHER");
        GitIssue issue = issue(f.repo(), 91, "Scoped issue", IssueState.OPEN,
                null, null, LocalDateTime.now());
        CommitData commit = commit(f.repo(), "aa".repeat(20), "local commit", LocalDateTime.now());
        CommitData foreign = commit(other.repo(), "bb".repeat(20), "foreign commit", LocalDateTime.now());
        String path = "/api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}";

        mockMvc.perform(post(path, f.project().getId(), issue.getId(), commit.getId())
                        .with(authentication(auth(ApplicationRole.STUDENT, f.leader().getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, f.project().getId(), issue.getId(), commit.getId())
                        .with(authentication(auth(ApplicationRole.STUDENT, f.member().getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path, f.project().getId(), issue.getId(), foreign.getId())
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TRACEABILITY_PROJECT_MISMATCH"));
    }

    private Fixture fixture(String prefix) {
        Lecturer lecturer = lecturer(prefix + "-LECTURER");
        Course course = courses.saveAndFlush(Course.builder()
                .instructor(lecturer)
                .courseCode(unique(prefix + "-COURSE"))
                .name(prefix + " Course")
                .build());
        Project project = projects.saveAndFlush(Project.builder()
                .course(course)
                .name(prefix + " Project")
                .build());
        Team team = teams.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name(prefix + " Team")
                .build());
        Student leader = student(prefix + "-LEADER");
        Student member = student(prefix + "-MEMBER");
        teamMembers.saveAndFlush(TeamMember.builder()
                .team(team).student(leader).roleInTeam(RoleInTeam.LEADER).build());
        teamMembers.saveAndFlush(TeamMember.builder()
                .team(team).student(member).roleInTeam(RoleInTeam.MEMBER).build());
        return new Fixture(project, team, lecturer, leader, member,
                repo(project, Math.abs(UUID.randomUUID().getMostSignificantBits()),
                        prefix.toLowerCase() + "/backend"));
    }

    private Lecturer lecturer(String prefix) {
        return lecturers.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Student student(String prefix) {
        return students.saveAndFlush(Student.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .studentCode(unique(prefix + "-CODE"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private GitRepo repo(Project project, long repositoryId, String fullName) {
        return gitRepos.saveAndFlush(GitRepo.builder()
                .project(project)
                .name(fullName.substring(fullName.indexOf('/') + 1))
                .fullName(fullName)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build());
    }

    private GitIssue issue(
            GitRepo repo,
            int number,
            String title,
            IssueState state,
            Student author,
            Student assignee,
            LocalDateTime updatedAt
    ) {
        return gitIssues.saveAndFlush(GitIssue.builder()
                .repo(repo)
                .githubIssueId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .issueNumber(number)
                .title(title)
                .state(state)
                .author(author)
                .assignee(assignee)
                .externalUpdatedAt(updatedAt)
                .closedAt(state == IssueState.CLOSED ? updatedAt : null)
                .build());
    }

    private Task task(Project project, String key, String title, LocalDateTime updatedAt) {
        return tasks.saveAndFlush(Task.builder()
                .project(project)
                .externalId(unique("JIRA-ID"))
                .externalKey(key)
                .title(title)
                .status(TaskStatus.IN_PROGRESS)
                .externalUpdatedAt(updatedAt)
                .build());
    }

    private PullRequest pull(GitRepo repo, int number, String title, LocalDateTime updatedAt) {
        return pullRequests.saveAndFlush(PullRequest.builder()
                .repo(repo)
                .githubPullRequestId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .pullNumber(number)
                .title(title)
                .status(PullRequestStatus.OPEN)
                .externalUpdatedAt(updatedAt)
                .build());
    }

    private CommitData commit(GitRepo repo, String sha, String message, LocalDateTime timestamp) {
        return commits.saveAndFlush(CommitData.builder()
                .repo(repo)
                .shaHash(sha)
                .githubCommitId(sha)
                .message(message)
                .timestamp(timestamp)
                .externalUpdatedAt(timestamp)
                .build());
    }

    private Authentication auth(ApplicationRole role, UUID profileId) {
        SagaPrincipal principal = new SagaPrincipal(
                unique(role.name().toLowerCase() + "-sub"),
                role.name().toLowerCase() + "@example.test",
                role.name(),
                role,
                profileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Fixture(
            Project project,
            Team team,
            Lecturer lecturer,
            Student leader,
            Student member,
            GitRepo repo
    ) {
    }
}
