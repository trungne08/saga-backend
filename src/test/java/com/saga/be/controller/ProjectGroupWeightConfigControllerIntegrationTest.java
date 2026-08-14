package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class ProjectGroupWeightConfigControllerIntegrationTest {

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
    private ProjectGroupWeightConfigRepository configRepository;

    @Test
    void lecturerOwningCourseCanUpdateGroupWeights() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "0.5", "0.3", "0.2", "initial split")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(fixture.project().getId().toString()))
                .andExpect(jsonPath("$.groupId").value(fixture.team().getId().toString()))
                .andExpect(jsonPath("$.codeWeight").value(0.5))
                .andExpect(jsonPath("$.note").value("initial split"));
    }

    @Test
    void adminCanUpdateGroupWeightsForAnyProject() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.ADMIN, UUID.randomUUID(), fixture.team().getId(),
                "0.4", "0.4", "0.2", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(0.4));
    }

    @Test
    void lecturerOutsideCourseIsForbidden() throws Exception {
        Fixture fixture = fixture();
        Lecturer otherLecturer = saveLecturer("OTHER-LECTURER");

        putAs(fixture, ApplicationRole.LECTURER, otherLecturer.getId(), fixture.team().getId(),
                "0.5", "0.3", "0.2", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void studentIncludingTeamLeaderIsForbidden() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.STUDENT, fixture.leader().getId(), fixture.team().getId(),
                "0.5", "0.3", "0.2", null)
                .andExpect(status().isForbidden());
        putAs(fixture, ApplicationRole.STUDENT, fixture.member().getId(), fixture.team().getId(),
                "0.5", "0.3", "0.2", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void mismatchedGroupIdFailsClosed() throws Exception {
        Fixture fixture = fixture();
        Team otherTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course())
                .name(unique("Other team"))
                .build());

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), otherTeam.getId(),
                "0.5", "0.3", "0.2", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_PROJECT_MISMATCH"));
    }

    @Test
    void negativeWeightIsRejected() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "-0.1", "0.6", "0.5", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_OUT_OF_RANGE"));
    }

    @Test
    void weightAboveOneIsRejected() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "1.5", "0.0", "0.0", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_OUT_OF_RANGE"));
    }

    @Test
    void sumNotEqualToOneIsRejected() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "0.5", "0.3", "0.1", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_SUM_INVALID"));
    }

    @Test
    void repeatedUpdateOnSameProjectIsDeterministicAndDoesNotDuplicateRows() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "0.5", "0.3", "0.2", "first")
                .andExpect(status().isOk());
        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), fixture.team().getId(),
                "0.2", "0.3", "0.5", "second")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(0.2))
                .andExpect(jsonPath("$.note").value("second"));

        assertEquals(1, configRepository.findAll().stream()
                .filter(config -> config.getProject().getId().equals(fixture.project().getId()))
                .count());
    }

    @Test
    void putRequiresAuthenticationAndValidCsrf() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.5", "0.3", "0.2", null))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.5", "0.3", "0.2", null))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.instructor().getId()))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions putAs(
            Fixture fixture,
            ApplicationRole role,
            UUID profileId,
            UUID groupId,
            String codeWeight,
            String documentWeight,
            String designWeight,
            String note
    ) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(groupId, codeWeight, documentWeight, designWeight, note))
                .with(authentication(authenticationFor(role, profileId)))
                .with(csrf()));
    }

    private String body(UUID groupId, String codeWeight, String documentWeight, String designWeight, String note) {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return "{"
                + "\"groupId\":\"" + groupId + "\","
                + "\"codeWeight\":" + codeWeight + ","
                + "\"documentWeight\":" + documentWeight + ","
                + "\"designWeight\":" + designWeight + ","
                + "\"note\":" + noteJson
                + "}";
    }

    private Fixture fixture() {
        Lecturer instructor = saveLecturer("OWNER-LECTURER");
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor)
                .courseCode(unique("COURSE"))
                .name("Group weight course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Group weight project")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name(unique("Team"))
                .build());
        Student leader = saveStudent("LEADER");
        Student member = saveStudent("MEMBER");
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(leader)
                .roleInTeam(RoleInTeam.LEADER)
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(member)
                .roleInTeam(RoleInTeam.MEMBER)
                .build());
        return new Fixture(course, project, team, instructor, leader, member);
    }

    private Lecturer saveLecturer(String prefix) {
        return lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix + " Lecturer")
                .build());
    }

    private Student saveStudent(String prefix) {
        return studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .studentCode(unique(prefix + "-CODE"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix + " Student")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-group-weight",
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
            Student member
    ) {
    }
}
