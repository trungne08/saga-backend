package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Class;
import com.saga.be.entity.Course;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Project;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.TeamContributionService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class AdminCourseReportExportIntegrationTest {

    private static final String EXPORT_PATH = "/api/admin/reports/courses/{courseId}/export";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private JiraBoardRepository jiraBoardRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PeerReviewRepository peerReviewRepository;
    @MockitoBean private JiraProviderClient jiraProviderClient;
    @MockitoBean private GitHubProviderClient gitHubProviderClient;
    @MockitoBean private TeamContributionService teamContributionService;

    @Test
    void exportIsAdminOnlyAndGetNeedsNoCsrf() throws Exception {
        UUID courseId = UUID.randomUUID();
        mockMvc.perform(get(EXPORT_PATH, courseId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EXPORT_PATH, courseId).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(EXPORT_PATH, courseId).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAndTombstonedCoursesAreNotExported() throws Exception {
        mockMvc.perform(get(EXPORT_PATH, UUID.randomUUID()).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isNotFound());
        Course tombstoned = courseRepository.saveAndFlush(Course.builder().courseCode("ARCHIVED")
                .name("Archived Course").deletedAt(LocalDateTime.now()).build());
        mockMvc.perform(get(EXPORT_PATH, tombstoned.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyCourseProducesAValidXlsxAttachment() throws Exception {
        Course emptyCourse = courseRepository.saveAndFlush(Course.builder().courseCode("EMPTY").name("Empty Course").build());

        MvcResult result = mockMvc.perform(get(EXPORT_PATH, emptyCourse.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("course-report-EMPTY.xlsx")))
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals(5, workbook.getNumberOfSheets());
            assertEquals("Course", workbook.getSheetAt(0).getSheetName());
            assertEquals("EMPTY", workbook.getSheet("Course").getRow(1).getCell(0).getStringCellValue());
            assertEquals(1, workbook.getSheet("Team Members").getPhysicalNumberOfRows());
            assertEquals(1, workbook.getSheet("Sprints").getPhysicalNumberOfRows());
        }
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient, teamContributionService);
    }

    @Test
    void exportUsesOnlySafeLocalCurrentDataAndOmitsSensitiveFields() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get(EXPORT_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("course-report-ALPHA.xlsx")))
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Row course = workbook.getSheet("Course").getRow(1);
            assertEquals("ALPHA", course.getCell(0).getStringCellValue());
            assertEquals("Semester Alpha", course.getCell(3).getStringCellValue());
            assertEquals("Subject Alpha", course.getCell(5).getStringCellValue());
            assertEquals("Class Alpha", course.getCell(7).getStringCellValue());
            assertEquals("Alpha Lecturer", course.getCell(8).getStringCellValue());

            Row member = workbook.getSheet("Team Members").getRow(1);
            assertEquals("Alpha Team", member.getCell(0).getStringCellValue());
            assertEquals("AL-01", member.getCell(1).getStringCellValue());
            assertEquals("LEADER", member.getCell(3).getStringCellValue());
            assertEquals("ACTIVE", member.getCell(4).getStringCellValue());

            Row sprint = workbook.getSheet("Sprints").getRow(1);
            assertEquals("Sprint Active", sprint.getCell(0).getStringCellValue());
            assertEquals("active", sprint.getCell(1).getStringCellValue());
            assertEquals(2, workbook.getSheet("Sprints").getPhysicalNumberOfRows());

            Row task = workbook.getSheet("Tasks").getRow(1);
            assertEquals("Alpha Project", task.getCell(0).getStringCellValue());
            assertEquals("Sprint Active", task.getCell(1).getStringCellValue());
            assertEquals("Implement export", task.getCell(2).getStringCellValue());
            assertEquals("AL-01", task.getCell(7).getStringCellValue());

            Row review = workbook.getSheet("Peer Reviews").getRow(1);
            assertEquals("Sprint Active", review.getCell(0).getStringCellValue());
            assertEquals("AL-01", review.getCell(1).getStringCellValue());
            assertEquals("AL-02", review.getCell(3).getStringCellValue());
            assertEquals("4", review.getCell(5).getStringCellValue());
            assertFalse(sheetText(workbook).contains("review comment must stay private"));
            assertFalse(sheetText(workbook).contains("alpha.student@example.test"));
            assertFalse(sheetText(workbook).contains("alpha-cognito-sub"));
            assertFalse(sheetText(workbook).contains("Current Contribution"));
            assertFalse(workbook.getSheetName(0).contains("Assessment"));
        }
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient, teamContributionService);
    }

    private Fixture fixture() {
        Lecturer lecturer = lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub("alpha-lecturer-sub")
                .email("alpha.lecturer@example.test").fullName("Alpha Lecturer").accountStatus(AccountStatus.ACTIVE).build());
        Semester semester = semesterRepository.saveAndFlush(Semester.builder().code("SEM-ALPHA").name("Semester Alpha").build());
        Subject subject = subjectRepository.saveAndFlush(Subject.builder().subjectCode("SUB-ALPHA").name("Subject Alpha").build());
        Class clazz = classRepository.saveAndFlush(Class.builder().classCode("CLS-ALPHA").name("Class Alpha").build());
        Course course = courseRepository.saveAndFlush(Course.builder().courseCode("ALPHA").name("Alpha Course")
                .semester(semester).subject(subject).clazz(clazz).instructor(lecturer).build());
        Project project = projectRepository.saveAndFlush(Project.builder().course(course).name("Alpha Project")
                .repositoryUrl("https://secret.example/repository").createdByCognitoSub("project-secret").build());
        Team team = teamRepository.saveAndFlush(Team.builder().course(course).project(project).name("Alpha Team").build());
        Student reviewer = student("alpha-cognito-sub", "AL-01", "Alpha Reviewer", "alpha.student@example.test");
        Student reviewee = student("beta-cognito-sub", "AL-02", "Alpha Reviewee", "beta.student@example.test");
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(reviewer).roleInTeam(RoleInTeam.LEADER).build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(reviewee).roleInTeam(RoleInTeam.MEMBER).build());
        JiraBoard board = jiraBoardRepository.saveAndFlush(JiraBoard.builder().project(project)
                .connectionStatus(IntegrationStatus.ACTIVE).encryptedAccessToken("never-export").build());
        Sprint activeSprint = sprintRepository.saveAndFlush(Sprint.builder().board(board).name("Sprint Active")
                .state("active").startDate(LocalDateTime.of(2026, 8, 1, 0, 0)).build());
        sprintRepository.saveAndFlush(Sprint.builder().board(board).name("Deleted Sprint").state("closed")
                .deletedAt(LocalDateTime.now()).build());
        taskRepository.saveAndFlush(Task.builder().project(project).sprint(activeSprint).assignee(reviewer)
                .title("Implement export").status(TaskStatus.IN_PROGRESS).type(TaskType.TASK).priority(Priority.HIGH)
                .storyPoint(3).dueDate(LocalDateTime.of(2026, 8, 10, 9, 0)).description("Private implementation detail")
                .externalId("provider-secret-id").build());
        peerReviewRepository.saveAndFlush(PeerReview.builder().sprint(activeSprint).reviewer(reviewer).reviewee(reviewee)
                .starRating(4).comment("review comment must stay private").build());
        return new Fixture(course);
    }

    private Student student(String cognitoSub, String studentCode, String fullName, String email) {
        return studentRepository.saveAndFlush(Student.builder().cognitoSub(cognitoSub).studentCode(studentCode)
                .fullName(fullName).email(email).accountStatus(AccountStatus.ACTIVE).build());
    }

    private String sheetText(XSSFWorkbook workbook) {
        StringBuilder text = new StringBuilder();
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            for (Row row : workbook.getSheetAt(sheetIndex)) {
                row.forEach(cell -> text.append(cell.getStringCellValue()).append('|'));
            }
        }
        return text.toString();
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-report", role.name().toLowerCase()
                + "@example.test", role.name() + " User", role, UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private record Fixture(Course course) {
    }
}
