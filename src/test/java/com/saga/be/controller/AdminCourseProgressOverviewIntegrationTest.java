package com.saga.be.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Project;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
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
class AdminCourseProgressOverviewIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private JiraBoardRepository jiraBoardRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private PeerReviewRepository peerReviewRepository;
    @MockitoBean private JiraProviderClient jiraProviderClient;
    @MockitoBean private GitHubProviderClient gitHubProviderClient;

    @Test
    void emptyDataIsPagedDeterministicAndAdminGetNeedsNoCsrf() throws Exception {
        mockMvc.perform(get("/api/admin/course-progress-overview")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
    }

    @Test
    void endpointIsAdminOnly() throws Exception {
        String path = "/api/admin/course-progress-overview";
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());
    }

    @Test
    void overviewAggregatesLocalCurrentCountsWithoutIncludingDeletedRowsOrProviderCalls() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get("/api/admin/course-progress-overview").param("page", "0").param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].courseId").value(fixture.alphaCourse().getId().toString()))
                .andExpect(jsonPath("$.content[0].courseCode").value("ALPHA"))
                .andExpect(jsonPath("$.content[0].lecturer.lecturerId").value(fixture.alphaLecturer().getId().toString()))
                .andExpect(jsonPath("$.content[0].teamCount").value(2))
                .andExpect(jsonPath("$.content[0].studentCount").value(2))
                .andExpect(jsonPath("$.content[0].projectCount").value(2))
                .andExpect(jsonPath("$.content[0].sprintCount").value(3))
                .andExpect(jsonPath("$.content[0].activeSprintCount").value(2))
                .andExpect(jsonPath("$.content[0].closedSprintCount").value(1))
                .andExpect(jsonPath("$.content[0].peerReviewCount").value(2));

        mockMvc.perform(get("/api/admin/course-progress-overview").param("page", "1").param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].courseCode").value("GAMMA"));

        mockMvc.perform(get("/api/admin/course-progress-overview").param("keyword", "alp")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courseCode").value("ALPHA"));
        mockMvc.perform(get("/api/admin/course-progress-overview")
                        .param("semesterId", fixture.alphaSemester().getId().toString())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courseCode").value("ALPHA"));
        mockMvc.perform(get("/api/admin/course-progress-overview")
                        .param("lecturerId", fixture.gammaLecturer().getId().toString())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courseCode").value("GAMMA"));

        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
    }

    @Test
    void invalidPaginationIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/course-progress-overview").param("size", "101")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    private Fixture fixture() {
        Lecturer alphaLecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub("overview-alpha-lecturer").email("alpha.lecturer@example.test").fullName("Alpha Lecturer")
                .accountStatus(AccountStatus.ACTIVE).build());
        Lecturer gammaLecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub("overview-gamma-lecturer").email("gamma.lecturer@example.test").fullName("Gamma Lecturer")
                .accountStatus(AccountStatus.ACTIVE).build());
        Semester alphaSemester = semesterRepository.saveAndFlush(Semester.builder().code("SEM-A").name("Semester A").build());
        Semester gammaSemester = semesterRepository.saveAndFlush(Semester.builder().code("SEM-G").name("Semester G").build());
        Course alpha = courseRepository.saveAndFlush(Course.builder().courseCode("ALPHA").name("Alpha Course")
                .semester(alphaSemester).instructor(alphaLecturer).build());
        courseRepository.saveAndFlush(Course.builder().courseCode("DELETED").name("Deleted Course")
                .semester(alphaSemester).instructor(alphaLecturer).deletedAt(LocalDateTime.now()).build());
        courseRepository.saveAndFlush(Course.builder().courseCode("GAMMA").name("Gamma Course")
                .semester(gammaSemester).instructor(gammaLecturer).build());

        Project firstProject = projectRepository.saveAndFlush(Project.builder().course(alpha).name("First Project").build());
        Project secondProject = projectRepository.saveAndFlush(Project.builder().course(alpha).name("Second Project").build());
        Team firstTeam = teamRepository.saveAndFlush(Team.builder().course(alpha).project(firstProject).name("First Team").build());
        Team secondTeam = teamRepository.saveAndFlush(Team.builder().course(alpha).project(secondProject).name("Second Team").build());
        Student firstStudent = student("overview-student-1", "OV-01");
        Student secondStudent = student("overview-student-2", "OV-02");
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(firstTeam).student(firstStudent).build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(firstTeam).student(secondStudent).build());

        JiraBoard firstBoard = jiraBoardRepository.saveAndFlush(JiraBoard.builder().project(firstProject)
                .connectionStatus(IntegrationStatus.ACTIVE).build());
        JiraBoard secondBoard = jiraBoardRepository.saveAndFlush(JiraBoard.builder().project(secondProject)
                .connectionStatus(IntegrationStatus.ACTIVE).build());
        Sprint firstActive = sprintRepository.saveAndFlush(Sprint.builder().board(firstBoard).name("Active one").state("active").build());
        Sprint firstClosed = sprintRepository.saveAndFlush(Sprint.builder().board(firstBoard).name("Closed").state("closed").build());
        sprintRepository.saveAndFlush(Sprint.builder().board(firstBoard).name("Deleted").state("active")
                .deletedAt(LocalDateTime.now()).build());
        Sprint secondActive = sprintRepository.saveAndFlush(Sprint.builder().board(secondBoard).name("Active two").state("active").build());

        PeerReview firstReview = peerReviewRepository.saveAndFlush(PeerReview.builder().sprint(firstActive)
                .reviewer(firstStudent).reviewee(secondStudent).starRating(4).build());
        peerReviewRepository.saveAndFlush(PeerReview.builder().sprint(secondActive)
                .reviewer(secondStudent).reviewee(firstStudent).starRating(5).build());
        entityManager.flush();
        return new Fixture(alpha, alphaSemester, alphaLecturer, gammaLecturer);
    }

    private Student student(String cognitoSub, String studentCode) {
        return studentRepository.saveAndFlush(Student.builder().cognitoSub(cognitoSub).studentCode(studentCode)
                .email(studentCode.toLowerCase() + "@example.test").fullName(studentCode)
                .accountStatus(AccountStatus.ACTIVE).build());
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-overview", role.name().toLowerCase()
                + "@example.test", role.name() + " User", role, UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private record Fixture(Course alphaCourse, Semester alphaSemester, Lecturer alphaLecturer,
                           Lecturer gammaLecturer) {
    }
}
