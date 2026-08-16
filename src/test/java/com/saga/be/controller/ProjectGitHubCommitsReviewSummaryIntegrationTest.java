package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.integration.provider.GitHubBranchCommitInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
class ProjectGitHubCommitsReviewSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LecturerRepository lecturerRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private GitHubInstallationRepository installationRepository;
    @Autowired
    private GitRepoRepository gitRepoRepository;
    @Autowired
    private CommitDataRepository commitDataRepository;
    @Autowired
    private CommitReviewIntentRepository commitReviewIntentRepository;
    @Autowired
    private CommitReviewResultRepository commitReviewResultRepository;

    @MockitoBean
    private GitHubProviderClient gitHubProviderClient;
    @MockitoBean
    private JiraProviderClient jiraProviderClient;

    @Test
    void reviewFieldIsPopulatedForACompletedIntentAndNullForAnUnknownCommit() throws Exception {
        Fixture fixture = fixture();
        CommitData commit = commitDataRepository.saveAndFlush(CommitData.builder()
                .repo(fixture.repo()).shaHash("known-sha").message("m").build());
        CommitReviewIntent intent = commitReviewIntentRepository.saveAndFlush(CommitReviewIntent.builder()
                .repo(fixture.repo()).commit(commit).shaHash("known-sha")
                .reviewMode(CommitReviewMode.LIVE_TASK_AWARE).priority(CommitReviewPriority.HIGH)
                .priorityRank(100).intentStatus(CommitReviewIntentStatus.COMPLETED)
                .startedAt(LocalDateTime.parse("2026-08-17T00:05:00"))
                .completedAt(LocalDateTime.parse("2026-08-17T00:06:00")).build());
        commitReviewResultRepository.saveAndFlush(CommitReviewResult.builder()
                .intent(intent).aiJobId(UUID.randomUUID()).projectId(fixture.project().getId())
                .repo(fixture.repo()).commit(commit).shaHash("known-sha").policyVersion("v1")
                .reviewMode("TASK_LINKED").traceabilityStatus("VERIFIED").messageQuality("GOOD")
                .codeQuality("GOOD").taskAlignment("ALIGNED").verdictEligible(true)
                .verdict("PASS").overallStatus("PASS").schemaVersion("commit-review-result-v2")
                .completedAt(LocalDateTime.parse("2026-08-17T00:06:00")).build());

        org.mockito.Mockito.when(gitHubProviderClient.branchCommits(
                        fixture.installation().getInstallationId(), "owner", "repo", "main"))
                .thenReturn(List.of(
                        new GitHubBranchCommitInfo("known-sha", "m", "n", "l", Instant.EPOCH, Instant.EPOCH, "u"),
                        new GitHubBranchCommitInfo("unknown-sha", "m2", "n", "l", Instant.EPOCH, Instant.EPOCH, "u")));

        mockMvc.perform(get("/api/projects/{projectId}/github/repositories/{repositoryId}/commits",
                        fixture.project().getId(), fixture.repo().getRepositoryId())
                        .param("branch", "main")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits.content[0].review.intentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.commits.content[0].review.reviewMode").value("TASK_LINKED"))
                .andExpect(jsonPath("$.commits.content[0].review.result.verdict").value("PASS"))
                .andExpect(jsonPath("$.commits.content[0].review.result.overallStatus").value("PASS"))
                .andExpect(jsonPath("$.commits.content[0].review.aiJobId").doesNotExist())
                .andExpect(jsonPath("$.commits.content[1].review").doesNotExist());
    }

    @Test
    void allowsAdminOwnerLecturerAndEveryMemberOfOwningTeamWithoutCsrf() throws Exception {
        Fixture fixture = fixture();
        org.mockito.Mockito.when(gitHubProviderClient.branchCommits(
                        fixture.installation().getInstallationId(), "owner", "repo", "main"))
                .thenReturn(List.of());

        getAs(fixture, ApplicationRole.ADMIN, UUID.randomUUID()).andExpect(status().isOk());
        getAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId()).andExpect(status().isOk());
        getAs(fixture, ApplicationRole.STUDENT, fixture.leader().getId()).andExpect(status().isOk());
        getAs(fixture, ApplicationRole.STUDENT, fixture.member().getId()).andExpect(status().isOk());
    }

    @Test
    void rejectsOutsiderLecturerAndStudentAndAnonymous() throws Exception {
        Fixture fixture = fixture();
        Lecturer otherLecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OTHER-LECTURER-SUB")).email(unique("other-lecturer") + "@example.test")
                .fullName("Other Lecturer").build());
        Student otherStudent = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique("OTHER-STUDENT-SUB")).studentCode(unique("OTHER-STUDENT-CODE"))
                .email(unique("other-student") + "@example.test").fullName("Other Student")
                .accountStatus(AccountStatus.ACTIVE).build());

        getAs(fixture, ApplicationRole.LECTURER, otherLecturer.getId()).andExpect(status().isForbidden());
        getAs(fixture, ApplicationRole.STUDENT, otherStudent.getId()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{projectId}/github/repositories/{repositoryId}/commits",
                        fixture.project().getId(), fixture.repo().getRepositoryId())
                        .param("branch", "main"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions getAs(
            Fixture fixture,
            ApplicationRole role,
            UUID profileId
    ) throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/github/repositories/{repositoryId}/commits",
                        fixture.project().getId(), fixture.repo().getRepositoryId())
                .param("branch", "main")
                .with(authentication(authenticationFor(role, profileId))));
    }

    private Fixture fixture() {
        Lecturer instructor = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OWNER-LECTURER-SUB")).email(unique("owner-lecturer") + "@example.test")
                .fullName("Owner Lecturer").build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor).courseCode(unique("COURSE")).name("Commit review course").build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course).name("Commit review project").build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course).project(project).name("Owning team").build());
        Student leader = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique("LEADER-SUB")).studentCode(unique("LEADER-CODE"))
                .email(unique("leader") + "@example.test").fullName("Leader")
                .accountStatus(AccountStatus.ACTIVE).build());
        Student member = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique("MEMBER-SUB")).studentCode(unique("MEMBER-CODE"))
                .email(unique("member") + "@example.test").fullName("Member")
                .accountStatus(AccountStatus.ACTIVE).build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(leader).roleInTeam(RoleInTeam.LEADER).build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(member).roleInTeam(RoleInTeam.MEMBER).build());
        GitHubInstallation installation = installationRepository.saveAndFlush(GitHubInstallation.builder()
                .installationId(System.nanoTime())
                .installedByCognitoSub(instructor.getCognitoSub())
                .installationStatus(GitHubInstallationStatus.ACTIVE)
                .build());
        GitRepo repo = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project).name("repo").ownerLogin("owner").fullName("owner/repo")
                .provider("GITHUB").repositoryId(System.nanoTime())
                .installation(installation).connectionStatus(IntegrationStatus.ACTIVE).build());
        return new Fixture(course, project, team, instructor, leader, member, installation, repo);
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-commit-review",
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                localProfileId,
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
            Course course,
            Project project,
            Team team,
            Lecturer instructor,
            Student leader,
            Student member,
            GitHubInstallation installation,
            GitRepo repo
    ) {
    }
}
